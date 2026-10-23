"""Offline validation tests and opt-in PostgreSQL transaction tests."""

import os
import unittest
from datetime import date
from decimal import Decimal
from unittest.mock import Mock, patch

import pandas as pd
import psycopg
from dotenv import load_dotenv

from analysis.scripts import import_data as importer

START = date(2020, 1, 1)
END = date(2020, 1, 4)


def history():
    return pd.DataFrame(
        {
            "Open": [100.1234567, 102.0],
            "High": [110.0, 112.0],
            "Low": [99.0, 100.0],
            "Close": [105.0, 108.0],
            "Adj Close": [95.0, 98.0],
            "Volume": [0, 1000],
        },
        index=pd.DatetimeIndex(["2020-01-02", "2020-01-03"], tz="America/New_York"),
    )


class ValidationTests(unittest.TestCase):
    def test_keeps_exchange_dates_separate_close_and_adjusted_and_zero_volume(self):
        frame = history()
        frame.index = frame.index + pd.Timedelta(hours=23)
        rows = importer.prepare_rows("AAPL", frame, START, END)
        self.assertEqual(rows[0][1], date(2020, 1, 2))
        self.assertEqual(rows[0][2], Decimal("100.123457"))
        self.assertEqual(rows[0][5:8], (Decimal("105.000000"), Decimal("95.000000"), 0))

    def test_rejects_empty_and_missing_adjusted_prices(self):
        for frame in (None, pd.DataFrame(), history().drop(columns=["Adj Close"])):
            with self.subTest(frame=type(frame).__name__), self.assertRaises(ValueError):
                importer.prepare_rows("AAPL", frame, START, END)

    def test_rejects_missing_nonfinite_nonpositive_and_unrepresentable_prices(self):
        for value in (None, pd.NA, float("nan"), float("inf"), -1, 0, "0.0000001", "1e14", "99999999999999.9999999"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                importer.price_value(value, "Close")

    def test_invalid_second_bar_rejects_the_whole_symbol(self):
        frame = history()
        frame.loc[frame.index[1], "Adj Close"] = float("nan")
        with self.assertRaisesRegex(ValueError, "2020-01-03"):
            importer.prepare_rows("AAPL", frame, START, END)

    def test_rejects_ohlc_outside_range(self):
        for field, value in (("Open", 113), ("Close", 98), ("High", 98)):
            frame = history()
            frame.loc[frame.index[0], field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "Low/High"):
                importer.prepare_rows("AAPL", frame, START, END)

    def test_rejects_fractional_negative_missing_and_overflow_volume(self):
        for value in (-1, "1.5", None, float("nan"), float("inf"), 2**63):
            with self.subTest(value=value), self.assertRaises(ValueError):
                importer.volume_value(value)

    def test_rejects_duplicate_and_timezone_missing_dates(self):
        duplicate = history()
        duplicate.index = pd.DatetimeIndex(["2020-01-02", "2020-01-02"], tz="America/New_York")
        naive = history()
        naive.index = naive.index.tz_localize(None)
        for frame in (duplicate, naive):
            with self.subTest(frame=frame.index), self.assertRaises(ValueError):
                importer.prepare_rows("AAPL", frame, START, END)

    def test_rejects_end_date_bar(self):
        with self.assertRaisesRegex(ValueError, "outside the requested range"):
            importer.prepare_rows("AAPL", history(), START, date(2020, 1, 3))


class SelectionAndFlowTests(unittest.TestCase):
    def test_default_long_range_and_normalized_subset(self):
        with patch.object(importer, "trading_today", return_value=date(2026, 10, 14)):
            args = importer.parse_args([])
            self.assertEqual((args.start, args.end), (date(2006, 1, 1), date(2026, 10, 14)))
            args = importer.parse_args(["--symbols", "aapl", "AAPL", "--years", "1"])
            self.assertEqual(args.symbols, ["AAPL"])
            self.assertEqual(args.start, date(2025, 10, 14))

    def test_environment_start_overrides_long_range_default(self):
        with patch.dict(os.environ, {"IMPORT_START_DATE": "2010-01-01"}), patch.object(importer, "trading_today", return_value=date(2026, 10, 14)):
            args = importer.parse_args([])
        self.assertEqual(args.start, date(2010, 1, 1))

    def test_leap_day_range(self):
        self.assertEqual(importer.years_before(date(2024, 2, 29), 1), date(2023, 2, 28))

    def test_rejects_future_and_reversed_ranges(self):
        with patch.object(importer, "trading_today", return_value=END), patch("sys.stderr"):
            for args in (["--end", "2020-01-05"], ["--start", "2020-01-03", "--end", "2020-01-03"]):
                with self.subTest(args=args), self.assertRaises(SystemExit):
                    importer.parse_args(args)

    def test_symbols_come_from_database_and_unknown_subset_fails(self):
        conn = Mock()
        conn.execute.return_value.fetchall.return_value = [("AAPL", "USD"), ("MSFT", "USD")]
        self.assertEqual(importer.load_instruments(conn, ["MSFT"]), [("MSFT", "USD")])
        with self.assertRaisesRegex(ValueError, "UNKNOWN"):
            importer.load_instruments(conn, ["UNKNOWN"])

    def test_fetch_uses_unadjusted_daily_bars_and_explicit_range(self):
        with patch.object(importer.yf, "Ticker") as ticker:
            importer.fetch_history("AAPL", START, END)
            self.assertEqual(ticker.call_args.args, ("AAPL",))
            options = ticker.return_value.history.call_args.kwargs
            self.assertFalse(options["auto_adjust"])
            self.assertEqual((options["interval"], options["start"], options["end"]), ("1d", "2020-01-01", "2020-01-04"))

    def test_failed_symbol_does_not_prevent_next_symbol(self):
        with patch.object(importer, "fetch_history", side_effect=[RuntimeError("source unavailable"), history()]), patch.object(importer, "upsert_prices") as upsert, self.assertLogs(importer.log, "ERROR") as logs:
            status = importer.run_import(Mock(), [("AAPL", "USD"), ("MSFT", "USD")], START, END)
        self.assertEqual(status, 1)
        self.assertIn("AAPL failed", logs.output[0])
        self.assertEqual(upsert.call_count, 1)
        self.assertEqual(upsert.call_args.args[1][0][0], "MSFT")

    def test_non_usd_instrument_is_reported_without_fetching(self):
        with patch.object(importer, "fetch_history") as fetch, self.assertLogs(importer.log, "ERROR"):
            self.assertEqual(importer.run_import(Mock(), [("OTHER", "EUR")], START, END), 1)
        fetch.assert_not_called()


@unittest.skipUnless(os.getenv("ANALYTICS_TEST_DB") == "1", "Set ANALYTICS_TEST_DB=1 for PostgreSQL verification")
class DatabaseTests(unittest.TestCase):
    def setUp(self):
        load_dotenv(importer.PROJECT_ROOT / ".env", override=False)
        self.conn = importer.connect()
        self.addCleanup(self.conn.close)
        transaction = self.conn.transaction(force_rollback=True)
        transaction.__enter__()
        self.addCleanup(transaction.__exit__, None, None, None)
        for symbol in ("HIST_TEST_A", "HIST_TEST_B"):
            self.conn.execute(
                "INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES (%s, %s, 'EQUITY', 'USD', TRUE)",
                (symbol, "Temporary importer test instrument"),
            )

    def count(self, symbol):
        return self.conn.execute("SELECT COUNT(*) FROM analytics.daily_prices WHERE symbol = %s", (symbol,)).fetchone()[0]

    def test_upsert_updates_prices_without_adding_rows(self):
        rows = importer.prepare_rows("HIST_TEST_A", history(), START, END)
        importer.upsert_prices(self.conn, rows)
        changed = history()
        changed["Close"] = [106.0, 109.0]
        changed["Adj Close"] = [96.0, 99.0]
        importer.upsert_prices(self.conn, importer.prepare_rows("HIST_TEST_A", changed, START, END))
        self.assertEqual(self.count("HIST_TEST_A"), 2)
        stored = self.conn.execute("SELECT close, adjusted_close, source, loaded_at IS NOT NULL FROM analytics.daily_prices WHERE symbol = 'HIST_TEST_A' ORDER BY price_date").fetchall()
        self.assertEqual(stored[0], (Decimal("106.000000"), Decimal("96.000000"), "yfinance", True))

    def test_database_failure_rolls_back_symbol_and_allows_next_write(self):
        rows = importer.prepare_rows("HIST_TEST_A", history(), START, END)
        rows[1] = (*rows[1][:-1], -1)
        with self.assertRaises(psycopg.errors.CheckViolation):
            importer.upsert_prices(self.conn, rows)
        self.assertEqual(self.count("HIST_TEST_A"), 0)
        importer.upsert_prices(self.conn, importer.prepare_rows("HIST_TEST_B", history(), START, END))
        self.assertEqual(self.count("HIST_TEST_B"), 2)

    def test_invalid_source_does_not_partially_write_or_block_next_symbol(self):
        invalid = history()
        invalid.loc[invalid.index[1], "Close"] = float("nan")
        with patch.object(importer, "fetch_history", side_effect=[invalid, history()]), self.assertLogs(importer.log, "ERROR"):
            status = importer.run_import(self.conn, [("HIST_TEST_A", "USD"), ("HIST_TEST_B", "USD")], START, END)
        self.assertEqual(status, 1)
        self.assertEqual(self.count("HIST_TEST_A"), 0)
        self.assertEqual(self.count("HIST_TEST_B"), 2)


if __name__ == "__main__":
    unittest.main()
