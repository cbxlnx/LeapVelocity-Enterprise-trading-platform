import json
import unittest
from datetime import datetime, timezone
from unittest import mock

from publisher import DEFAULT_SYMBOLS, build_quote, fetch_quotes, load_settings, parse_symbols, publish

FETCHED_AT = datetime(2026, 10, 14, 14, 30, tzinfo=timezone.utc)
AS_OF_EPOCH = 1791988200  # 2026-10-14T14:30:00Z


def two_sided_info(**overrides):
    info = {
        "regularMarketPrice": 187.23,
        "bid": 187.20,
        "ask": 187.25,
        "currency": "USD",
        "regularMarketTime": AS_OF_EPOCH,
    }
    info.update(overrides)
    return info


class FakeTicker:
    """Stands in for yf.Ticker. Reading info raises when the response for the symbol is an exception."""

    def __init__(self, symbol, responses):
        self._symbol = symbol
        self._responses = responses

    @property
    def info(self):
        response = self._responses[self._symbol]
        if isinstance(response, Exception):
            raise response
        return response


class FakeProducer:
    def __init__(self):
        self.sent = []

    def produce(self, topic, key, value, on_delivery):
        self.sent.append((topic, key, json.loads(value)))

    def poll(self, timeout):
        return 0


class ParseSymbolsTest(unittest.TestCase):
    def test_trims_uppercases_and_drops_duplicates(self):
        self.assertEqual(parse_symbols(" aapl, MSFT ,aapl,, "), ["AAPL", "MSFT"])

    def test_rejects_empty_list(self):
        with self.assertRaises(ValueError):
            parse_symbols(" , ")

    def test_rejects_symbol_longer_than_column(self):
        with self.assertRaises(ValueError):
            parse_symbols("X" * 21)


class LoadSettingsTest(unittest.TestCase):
    def test_defaults(self):
        settings = load_settings({})
        self.assertEqual(settings.bootstrap_servers, "kafka:29092")
        self.assertEqual(settings.topic, "market-data")
        self.assertEqual(settings.poll_seconds, 5.0)
        self.assertEqual(settings.symbols, DEFAULT_SYMBOLS.split(","))

    def test_blank_symbols_fall_back_to_defaults(self):
        settings = load_settings({"MARKET_DATA_SYMBOLS": ""})
        self.assertEqual(settings.symbols, DEFAULT_SYMBOLS.split(","))

    def test_reads_overrides(self):
        settings = load_settings({
            "KAFKA_BOOTSTRAP_SERVERS": "broker:9092",
            "MARKET_DATA_TOPIC": "quotes",
            "MARKET_DATA_SYMBOLS": "spy,qqq",
            "MARKET_DATA_POLL_SECONDS": "2.5",
        })
        self.assertEqual(settings.bootstrap_servers, "broker:9092")
        self.assertEqual(settings.topic, "quotes")
        self.assertEqual(settings.symbols, ["SPY", "QQQ"])
        self.assertEqual(settings.poll_seconds, 2.5)

    def test_rejects_non_positive_poll_interval(self):
        with self.assertRaises(ValueError):
            load_settings({"MARKET_DATA_POLL_SECONDS": "0"})


class BuildQuoteTest(unittest.TestCase):
    def test_maps_two_sided_quote_to_contract(self):
        quote = build_quote("AAPL", two_sided_info(), FETCHED_AT)
        self.assertEqual(quote, {
            "symbol": "AAPL",
            "lastPrice": 187.23,
            "bid": 187.2,
            "ask": 187.25,
            "currency": "USD",
            "asOf": "2026-10-14T14:30:00Z",
            "source": "yfinance",
        })

    def test_uses_fetch_time_when_source_time_is_missing(self):
        info = two_sided_info()
        del info["regularMarketTime"]
        quote = build_quote("AAPL", info, FETCHED_AT)
        self.assertEqual(quote["asOf"], "2026-10-14T14:30:00Z")

    def test_falls_back_to_current_price_for_last_price(self):
        info = two_sided_info()
        del info["regularMarketPrice"]
        info["currentPrice"] = 10.0
        self.assertEqual(build_quote("AAPL", info, FETCHED_AT)["lastPrice"], 10.0)

    def test_skips_missing_or_zero_bid(self):
        self.assertIsNone(build_quote("AAPL", two_sided_info(bid=0), FETCHED_AT))
        self.assertIsNone(build_quote("AAPL", two_sided_info(bid=None), FETCHED_AT))

    def test_skips_missing_or_zero_ask(self):
        self.assertIsNone(build_quote("AAPL", two_sided_info(ask=0), FETCHED_AT))
        self.assertIsNone(build_quote("AAPL", two_sided_info(ask=None), FETCHED_AT))

    def test_skips_crossed_market(self):
        self.assertIsNone(build_quote("AAPL", two_sided_info(bid=187.30, ask=187.25), FETCHED_AT))

    def test_skips_missing_last_price(self):
        info = two_sided_info()
        del info["regularMarketPrice"]
        self.assertIsNone(build_quote("AAPL", info, FETCHED_AT))

    def test_skips_missing_currency(self):
        self.assertIsNone(build_quote("AAPL", two_sided_info(currency=None), FETCHED_AT))


class FetchQuotesTest(unittest.TestCase):
    def test_failing_and_one_sided_symbols_do_not_stop_others(self):
        responses = {
            "AAPL": two_sided_info(),
            "BAD": RuntimeError("rate limited"),
            "MSFT": two_sided_info(bid=0),
            "SPY": two_sided_info(),
        }
        with mock.patch("publisher.yf.Ticker", side_effect=lambda symbol: FakeTicker(symbol, responses)):
            quotes = fetch_quotes(["AAPL", "BAD", "MSFT", "SPY"])
        self.assertEqual([quote["symbol"] for quote in quotes], ["AAPL", "SPY"])


class PublishTest(unittest.TestCase):
    def test_keys_each_message_by_symbol(self):
        producer = FakeProducer()
        quote = build_quote("AAPL", two_sided_info(), FETCHED_AT)
        publish(producer, "market-data", [quote])
        self.assertEqual(producer.sent, [("market-data", b"AAPL", quote)])


if __name__ == "__main__":
    unittest.main()
