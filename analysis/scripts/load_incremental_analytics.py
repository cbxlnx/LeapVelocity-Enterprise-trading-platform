import json
import os
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

import psycopg
from psycopg.rows import dict_row

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "analytics_loader"
STATE_FILE = DATA_DIR / "fact_trades_loader_state.json"
DEAD_LETTER_DIR = DATA_DIR / "dead_letter"
ALLOWED_SIDES = {"BUY", "SELL"}


def connect():
    password = os.getenv("DB_PASSWORD")
    if not password:
        raise RuntimeError("DB_PASSWORD must be set")

    return psycopg.connect(
        dbname=os.getenv("DB_NAME", "leapvelocity_trading_db"),
        user=os.getenv("DB_USER", "postgres"),
        password=password,
        host=os.getenv("DB_HOST", "localhost"),
        port=os.getenv("POSTGRES_PORT", "5432"),
        row_factory=dict_row,
    )


def load_watermark():
    if not STATE_FILE.exists():
        return None

    payload = json.loads(STATE_FILE.read_text(encoding="utf-8"))
    return {
        "executed_on": datetime.fromisoformat(payload["executed_on"]),
        "execution_id": payload["execution_id"],
    }


def save_watermark(watermark):
    STATE_FILE.write_text(
        json.dumps(
            {
                "executed_on": watermark["executed_on"].isoformat(timespec="seconds"),
                "execution_id": watermark["execution_id"],
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def validate_trade(row):
    if row["executed_on"] is None:
        return "missing executed_on"
    if row["external_account_id"] is None or row["holder_name"] is None:
        return f"missing account for account_id {row['source_account_id']}"
    if row["instrument_name"] is None or row["asset_class"] is None or row["currency"] is None:
        return f"missing instrument for symbol {row['symbol']}"
    if row["side"] not in ALLOWED_SIDES:
        return f"side must be BUY or SELL, was {row['side']}"
    if row["quantity"] <= 0:
        return f"quantity must be positive, was {row['quantity']}"
    if row["price"] <= 0:
        return f"price must be positive, was {row['price']}"
    return None


def upsert_dimensions(conn, rows):
    if not rows:
        return

    dim_accounts = {}
    dim_instruments = {}
    dim_dates = {}

    for row in rows:
        effective_date = (row["account_last_updated"] or row["executed_on"]).date()
        trade_date = row["executed_on"].date()
        date_key = int(row["executed_on"].strftime("%Y%m%d"))

        dim_accounts[row["source_account_id"]] = (
            row["source_account_id"],
            row["external_account_id"],
            row["holder_name"],
            row["account_status"],
            effective_date,
        )
        dim_instruments[row["symbol"]] = (
            row["symbol"],
            row["instrument_name"],
            row["asset_class"],
            row["currency"],
        )
        dim_dates[date_key] = (
            date_key,
            trade_date,
            trade_date.day,
            trade_date.month,
            trade_date.year,
            ((trade_date.month - 1) // 3) + 1,
        )

    with conn.cursor() as cur:
        cur.executemany(
            """
            INSERT INTO analytics.dim_account
                (account_id, external_account_id, holder_name, status, effective_date)
            VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (account_id) DO UPDATE
            SET external_account_id = EXCLUDED.external_account_id,
                holder_name = EXCLUDED.holder_name,
                status = EXCLUDED.status,
                effective_date = EXCLUDED.effective_date
            """,
            dim_accounts.values(),
        )

        cur.executemany(
            """
            INSERT INTO analytics.dim_instrument
                (symbol, name, asset_class, currency)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (symbol) DO UPDATE
            SET name = EXCLUDED.name,
                asset_class = EXCLUDED.asset_class,
                currency = EXCLUDED.currency
            """,
            dim_instruments.values(),
        )

        cur.executemany(
            """
            INSERT INTO analytics.dim_date
                (date_key, full_date, day, month, year, quarter)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (date_key) DO UPDATE
            SET full_date = EXCLUDED.full_date,
                day = EXCLUDED.day,
                month = EXCLUDED.month,
                year = EXCLUDED.year,
                quarter = EXCLUDED.quarter
            """,
            dim_dates.values(),
        )


def upsert_fact_trades(conn, rows):
    inserted = 0
    if not rows:
        return inserted

    with conn.cursor() as cur:
        for row in rows:
            date_key = int(row["executed_on"].strftime("%Y%m%d"))
            cur.execute(
                """
                INSERT INTO analytics.fact_trades
                    (source_execution_id, account_key, instrument_key, date_key, side, quantity, price, status)
                VALUES (
                    %s,
                    (SELECT account_key FROM analytics.dim_account WHERE account_id = %s),
                    (SELECT instrument_key FROM analytics.dim_instrument WHERE symbol = %s),
                    %s,
                    %s,
                    %s,
                    %s,
                    'FILLED'
                )
                ON CONFLICT (source_execution_id) DO NOTHING
                """,
                (
                    row["source_execution_id"],
                    row["source_account_id"],
                    row["symbol"],
                    date_key,
                    row["side"],
                    row["quantity"],
                    row["price"],
                ),
            )
            inserted += cur.rowcount

    return inserted


def write_dead_letter(rows, batch_id):
    if not rows:
        return None

    DEAD_LETTER_DIR.mkdir(parents=True, exist_ok=True)
    output_path = DEAD_LETTER_DIR / f"fact_trades_dead_letter_{batch_id}.jsonl"
    with output_path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, default=json_default) + "\n")
    return output_path


def json_default(value):
    if isinstance(value, datetime):
        return value.isoformat(timespec="seconds")
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, Decimal):
        return str(value)
    raise TypeError(f"Object of type {type(value).__name__} is not JSON serializable")


def main():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    batch_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    watermark = load_watermark()
    base_query = """
        SELECT
            e.id::text AS source_execution_id,
            e.order_id::text AS source_order_id,
            e.account_id AS source_account_id,
            a.account_id AS external_account_id,
            a.holder_name,
            a.status AS account_status,
            a.last_updated AS account_last_updated,
            e.symbol,
            i.name AS instrument_name,
            i.asset_class,
            i.currency,
            e.side,
            e.quantity,
            e.price,
            e.executed_on
        FROM executions e
        LEFT JOIN accounts a ON a.id = e.account_id
        LEFT JOIN instruments i ON i.symbol = e.symbol
    """

    if watermark is None:
        query = base_query + " ORDER BY e.executed_on, e.id::text"
        params = ()
    else:
        query = (
            base_query
            + """
            WHERE e.executed_on > %s
               OR (e.executed_on = %s AND e.id::text > %s)
            ORDER BY e.executed_on, e.id::text
            """
        )
        params = (
            watermark["executed_on"],
            watermark["executed_on"],
            watermark["execution_id"],
        )

    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(query, params)
            candidate_rows = cur.fetchall()

        valid_rows = []
        quarantined_rows = []
        for row in candidate_rows:
            reason = validate_trade(row)
            if reason is None:
                valid_rows.append(row)
                continue
            quarantined_rows.append(
                {
                    "batch_id": batch_id,
                    "source_execution_id": row["source_execution_id"],
                    "source_order_id": row["source_order_id"],
                    "reason": reason,
                    "raw_row": row,
                }
            )

        inserted_fact_rows = 0
        if valid_rows:
            with conn.transaction():
                upsert_dimensions(conn, valid_rows)
                inserted_fact_rows = upsert_fact_trades(conn, valid_rows)

        dead_letter_path = write_dead_letter(quarantined_rows, batch_id)
        if candidate_rows:
            last_row = candidate_rows[-1]
            save_watermark(
                {
                    "executed_on": last_row["executed_on"],
                    "execution_id": last_row["source_execution_id"],
                }
            )

    print(f"batch_id={batch_id}")
    print(f"candidate_rows={len(candidate_rows)}")
    print(f"valid_rows={len(valid_rows)}")
    print(f"inserted_fact_rows={inserted_fact_rows}")
    print(f"quarantined_rows={len(quarantined_rows)}")
    if candidate_rows:
        last_row = candidate_rows[-1]
        print(
            "watermark="
            f"{last_row['executed_on'].isoformat(timespec='seconds')}|{last_row['source_execution_id']}"
        )
    else:
        print("watermark=unchanged")
    if dead_letter_path is not None:
        print(f"dead_letter_file={dead_letter_path}")
    print(f"state_file={STATE_FILE}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
