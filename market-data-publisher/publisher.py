"""Publishes live quotes from yfinance to the Kafka market-data topic.

Every poll cycle fetches each configured symbol and publishes one message per
symbol, keyed by symbol. The message shape is the market-data contract in
docs/event-flow.md. Symbols without a two-sided quote are skipped for that cycle.
"""

import json
import logging
import math
import os
import signal
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import List, Mapping, Optional

import yfinance as yf
from confluent_kafka import KafkaException, Producer

log = logging.getLogger("market-data-publisher")

DEFAULT_SYMBOLS = "AAPL,MSFT,GOOGL,AMZN,NVDA,TSLA,JPM,JNJ,XOM,WMT,SPY,QQQ"
DEFAULT_TOPIC = "market-data"
DEFAULT_BOOTSTRAP_SERVERS = "kafka:29092"
DEFAULT_POLL_SECONDS = 5.0
MAX_SYMBOL_LENGTH = 20  # matches instruments.symbol VARCHAR(20)
TOPIC_WAIT_SECONDS = 5.0
SOURCE = "yfinance"


@dataclass(frozen=True)
class Settings:
    bootstrap_servers: str
    topic: str
    symbols: List[str]
    poll_seconds: float


def parse_symbols(raw: str) -> List[str]:
    symbols: List[str] = []
    for part in raw.split(","):
        symbol = part.strip().upper()
        if not symbol:
            continue
        if len(symbol) > MAX_SYMBOL_LENGTH:
            raise ValueError(f"Symbol is longer than {MAX_SYMBOL_LENGTH} characters: {symbol}")
        if symbol not in symbols:
            symbols.append(symbol)
    if not symbols:
        raise ValueError("MARKET_DATA_SYMBOLS must list at least one symbol")
    return symbols


def load_settings(env: Mapping[str, str] = os.environ) -> Settings:
    poll_seconds = float(env.get("MARKET_DATA_POLL_SECONDS", DEFAULT_POLL_SECONDS))
    if poll_seconds <= 0:
        raise ValueError("MARKET_DATA_POLL_SECONDS must be greater than zero")
    return Settings(
        bootstrap_servers=env.get("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS),
        topic=env.get("MARKET_DATA_TOPIC", DEFAULT_TOPIC),
        symbols=parse_symbols(env.get("MARKET_DATA_SYMBOLS") or DEFAULT_SYMBOLS),
        poll_seconds=poll_seconds,
    )


def _positive_number(value) -> Optional[float]:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(number) or number <= 0:
        return None
    return number


def _as_of(regular_market_time, fallback: datetime) -> datetime:
    seconds = _positive_number(regular_market_time)
    if seconds is None:
        return fallback
    return datetime.fromtimestamp(seconds, tz=timezone.utc)


def build_quote(symbol: str, info: Mapping, fetched_at: datetime) -> Optional[dict]:
    """Map a yfinance info record to the market-data contract.

    Returns None unless the quote is two-sided: bid and ask both positive and ask >= bid.
    """
    last_price = _positive_number(info.get("regularMarketPrice") or info.get("currentPrice"))
    previous_close = _positive_number(info.get("regularMarketPreviousClose"))
    bid = _positive_number(info.get("bid"))
    ask = _positive_number(info.get("ask"))
    day_high = _positive_number(info.get("dayHigh"))
    day_low = _positive_number(info.get("dayLow"))
    volume = info.get("regularMarketVolume")
    currency = info.get("currency")
    if last_price is None or bid is None or ask is None or ask < bid or not currency:
        return None
    as_of = _as_of(info.get("regularMarketTime"), fetched_at)
    try:
        volume = int(volume) if volume is not None and int(volume) >= 0 else None
    except (TypeError, ValueError):
        volume = None
    change = round(last_price - previous_close, 4) if previous_close is not None else None
    change_percent = round((change / previous_close) * 100, 4) if change is not None else None
    return {
        "symbol": symbol,
        "lastPrice": round(last_price, 4),
        "bid": round(bid, 4),
        "ask": round(ask, 4),
        "currency": currency,
        "asOf": as_of.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": SOURCE,
        "change": change,
        "changePercent": change_percent,
        "dayHigh": round(day_high, 4) if day_high is not None else None,
        "dayLow": round(day_low, 4) if day_low is not None else None,
        "volume": volume,
    }


def fetch_quotes(symbols: List[str]) -> List[dict]:
    """Fetch one quote per symbol. A failing symbol is logged and skipped so the others still publish."""
    quotes = []
    for symbol in symbols:
        try:
            info = yf.Ticker(symbol).info or {}
        except Exception as exc:  # yfinance raises assorted errors (network, rate limit, bad payload)
            log.warning("Quote fetch failed for %s: %s", symbol, exc)
            continue
        quote = build_quote(symbol, info, datetime.now(timezone.utc))
        if quote is None:
            log.debug("No two-sided quote for %s this cycle", symbol)
        else:
            quotes.append(quote)
    return quotes


def create_producer(settings: Settings) -> Producer:
    return Producer({
        "bootstrap.servers": settings.bootstrap_servers,
        "client.id": "market-data-publisher",
        "acks": "all",
        "enable.idempotence": True,
        "message.timeout.ms": 30000,
    })


def _report_delivery(err, msg) -> None:
    if err is not None:
        log.warning("Quote delivery failed for %s: %s", msg.key().decode("utf-8"), err)


def wait_for_topic(producer: Producer, topic: str, stop: threading.Event) -> bool:
    """Block until the topic exists. Kafka auto-create is disabled; trade-api and execution-engine declare it on startup."""
    while not stop.is_set():
        try:
            topic_metadata = producer.list_topics(topic=topic, timeout=10).topics.get(topic)
            if topic_metadata is not None and topic_metadata.error is None:
                log.info("Topic %s is available", topic)
                return True
            log.info("Waiting for topic %s to be created", topic)
        except KafkaException as exc:
            log.warning("Kafka not reachable yet: %s", exc)
        stop.wait(TOPIC_WAIT_SECONDS)
    return False


def publish(producer: Producer, topic: str, quotes: List[dict]) -> None:
    for quote in quotes:
        while True:
            try:
                producer.produce(
                    topic,
                    key=quote["symbol"].encode("utf-8"),
                    value=json.dumps(quote).encode("utf-8"),
                    on_delivery=_report_delivery,
                )
                break
            except BufferError:
                producer.poll(1)  # local queue is full; serve deliveries to drain it
        producer.poll(0)


def run(settings: Settings, producer: Producer, stop: threading.Event) -> None:
    if not wait_for_topic(producer, settings.topic, stop):
        return
    last_count = None
    while not stop.is_set():
        started = time.monotonic()
        quotes = fetch_quotes(settings.symbols)
        publish(producer, settings.topic, quotes)
        producer.flush(10)
        if len(quotes) != last_count:
            log.info("Sent %d of %d quotes", len(quotes), len(settings.symbols))
            last_count = len(quotes)
        stop.wait(max(0.0, settings.poll_seconds - (time.monotonic() - started)))


def main() -> None:
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    settings = load_settings()
    stop = threading.Event()
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda *_: stop.set())
    producer = create_producer(settings)
    log.info("Publishing %d symbols to %s every %.1fs", len(settings.symbols), settings.topic, settings.poll_seconds)
    try:
        run(settings, producer, stop)
    finally:
        producer.flush(10)
        log.info("Market-data publisher stopped")


if __name__ == "__main__":
    main()
