"""Import daily yfinance prices for database instruments into PostgreSQL.

Run with --symbols AAPL --years 1 for the first import, or without arguments
for two years of all eligible instruments. No downloads happen on module import.
"""

import argparse
import logging
import os
from datetime import date, datetime
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path
from zoneinfo import ZoneInfo

import psycopg
import yfinance as yf
from dotenv import load_dotenv

log = logging.getLogger("historical-price-importer")
PROJECT_ROOT = Path(__file__).resolve().parents[2]
PRICE_SCALE = Decimal("0.000001")
REQUIRED_COLUMNS = {"Open", "High", "Low", "Close", "Adj Close", "Volume"}
UPSERT_SQL = """
    INSERT INTO analytics.daily_prices
        (symbol, price_date, open, high, low, close, adjusted_close, volume, source)
    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'yfinance')
    ON CONFLICT (symbol, price_date) DO UPDATE
    SET open = EXCLUDED.open,
        high = EXCLUDED.high,
        low = EXCLUDED.low,
        close = EXCLUDED.close,
        adjusted_close = EXCLUDED.adjusted_close,
        volume = EXCLUDED.volume,
        source = EXCLUDED.source,
        loaded_at = NOW()
"""


def trading_today():
    return datetime.now(ZoneInfo("America/New_York")).date()


def years_before(day, years):
    try:
        return day.replace(year=day.year - years)
    except ValueError:  # February 29 in a non-leap target year.
        return day.replace(year=day.year - years, day=28)


def parse_date(value):
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("Use a date in YYYY-MM-DD format") from exc


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--symbols", nargs="+", help="Subset of database symbols, e.g. AAPL MSFT")
    period = parser.add_mutually_exclusive_group()
    period.add_argument("--years", type=int, choices=range(1, 11), default=2)
    period.add_argument("--start", type=parse_date, help="Inclusive start date")
    parser.add_argument("--end", type=parse_date, help="Exclusive end date; default: today in New York")
    args = parser.parse_args(argv)
    today = trading_today()
    args.end = args.end or today
    args.start = args.start or years_before(args.end, args.years)
    if args.end > today:
        parser.error("--end cannot be later than today in New York")
    if args.start >= args.end:
        parser.error("The start date must be earlier than the exclusive end date")
    if args.symbols:
        args.symbols = list(dict.fromkeys(symbol.strip().upper() for symbol in args.symbols))
    return args


def connect():
    password = os.getenv("DB_PASSWORD")
    if not password:
        raise RuntimeError("DB_PASSWORD must be set in the environment or the project .env")
    return psycopg.connect(
        dbname=os.getenv("DB_NAME", "leapvelocity_trading_db"),
        user=os.getenv("DB_USER", "postgres"),
        password=password,
        host=os.getenv("DB_HOST", "localhost"),
        port=os.getenv("DB_PORT") or os.getenv("POSTGRES_PORT", "5432"),
        connect_timeout=10,
        autocommit=True,
    )


def load_instruments(conn, symbols=None):
    instruments = conn.execute(
        "SELECT symbol, currency FROM instruments "
        "WHERE tradable = TRUE AND asset_class <> 'CASH' ORDER BY symbol"
    ).fetchall()
    if symbols:
        unknown = set(symbols) - {symbol for symbol, _ in instruments}
        if unknown:
            raise ValueError("Symbols are not eligible database instruments: " + ", ".join(sorted(unknown)))
        instruments = [(symbol, currency) for symbol, currency in instruments if symbol in symbols]
    if not instruments:
        raise ValueError("No eligible instruments found in the database")
    return instruments


def fetch_history(symbol, start, end):
    return yf.Ticker(symbol).history(
        start=start.isoformat(),
        end=end.isoformat(),
        interval="1d",
        auto_adjust=False,
        actions=False,
        keepna=True,
        timeout=30,
        raise_errors=True,
    )


def decimal_value(value, field):
    try:
        number = Decimal(str(value))
    except InvalidOperation as exc:
        raise ValueError(f"{field} is missing or is not a number") from exc
    if not number.is_finite():
        raise ValueError(f"{field} must be finite")
    return number


def price_value(value, field):
    number = decimal_value(value, field)
    if number <= 0 or number >= Decimal("1e14"):
        raise ValueError(f"{field} must be positive and fit NUMERIC(20,6)")
    number = number.quantize(PRICE_SCALE, rounding=ROUND_HALF_UP)
    if number <= 0 or number >= Decimal("1e14"):
        raise ValueError(f"{field} is outside the supported range after rounding")
    return number


def volume_value(value):
    number = decimal_value(value, "Volume")
    if number < 0 or number != number.to_integral_value() or number > 2**63 - 1:
        raise ValueError("Volume must be a non-negative integer that fits BIGINT")
    return int(number)


def prepare_rows(symbol, history, start, end):
    """Validate the whole symbol before writing; never invent missing prices."""
    if history is None or history.empty:
        raise ValueError("No historical prices returned")
    missing = REQUIRED_COLUMNS - set(history.columns)
    if missing:
        raise ValueError("Missing history columns: " + ", ".join(sorted(missing)))

    rows = []
    seen_dates = set()
    for timestamp, bar in history.sort_index().iterrows():
        if not isinstance(timestamp, datetime) or timestamp.tzinfo is None:
            raise ValueError("History must have timezone-aware trading dates")
        trade_date = timestamp.date()  # Preserve the source's exchange-local date.
        if not isinstance(trade_date, date) or isinstance(trade_date, datetime):
            raise ValueError("Invalid trading date")
        if not start <= trade_date < end:
            raise ValueError(f"Trading date {trade_date} is outside the requested range")
        if trade_date in seen_dates:
            raise ValueError(f"Duplicate trading date {trade_date} in the source response")
        seen_dates.add(trade_date)
        try:
            opening = price_value(bar["Open"], "Open")
            high = price_value(bar["High"], "High")
            low = price_value(bar["Low"], "Low")
            close = price_value(bar["Close"], "Close")
            adjusted = price_value(bar["Adj Close"], "Adj Close")
            volume = volume_value(bar["Volume"])
            if not low <= opening <= high or not low <= close <= high:
                raise ValueError("Open and Close must lie within Low/High")
        except ValueError as exc:
            raise ValueError(f"Invalid bar on {trade_date}: {exc}") from exc
        rows.append((symbol, trade_date, opening, high, low, close, adjusted, volume))
    return rows


def upsert_prices(conn, rows):
    # Autocommit keeps the instrument lookup outside this transaction. Each
    # symbol commits independently, and a database error rolls back its batch.
    with conn.transaction():
        with conn.cursor() as cur:
            cur.executemany(UPSERT_SQL, rows)


def run_import(conn, instruments, start, end):
    succeeded = failed = total_rows = 0
    for symbol, currency in instruments:
        try:
            if currency != "USD":
                raise ValueError(f"MVP supports USD instruments; database currency is {currency}")
            history = fetch_history(symbol, start, end)
            rows = prepare_rows(symbol, history, start, end)
            upsert_prices(conn, rows)
        except Exception as exc:
            failed += 1
            log.error("%s failed: %s", symbol, exc)
            continue
        succeeded += 1
        total_rows += len(rows)
        log.info("%s: upserted %d daily bars (%s through %s)", symbol, len(rows), rows[0][1], rows[-1][1])
    log.info("Finished: symbols_ok=%d symbols_failed=%d rows_upserted=%d", succeeded, failed, total_rows)
    return 1 if failed else 0


def main(argv=None):
    args = parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    load_dotenv(PROJECT_ROOT / ".env", override=False)
    try:
        with connect() as conn:
            if conn.execute("SELECT to_regclass('analytics.daily_prices')").fetchone()[0] is None:
                raise RuntimeError("Apply db/tables/12_daily_prices.sql before importing prices")
            instruments = load_instruments(conn, args.symbols)
            log.info("Importing %d instruments: %s to %s (end exclusive)", len(instruments), args.start, args.end)
            return run_import(conn, instruments, args.start, args.end)
    except (ValueError, RuntimeError, psycopg.Error) as exc:
        log.error("Import could not start: %s", exc)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
