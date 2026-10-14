# Execution Engine

A small Spring Boot service that stands in for a real exchange. It is step
2 of the order flow (see [`../docs/event-flow.md`](../docs/event-flow.md)):

1. Read an accepted order from the `orders` topic.
2. Fill it against the latest live quote for its symbol from the `market-data`
   topic, if that quote is marketable against the order's limit.
3. Write the fill to the `executions` topic, keyed by account.

It has no database and no REST API of its own, only an actuator health
endpoint used by the Docker health check.

## What's here

```
execution-engine/
├── Dockerfile                        # multi-stage: Maven build, then JRE 21 runtime
├── pom.xml                           # Spring Boot 3.5, Spring Kafka
└── src/
    ├── main/java/com/neueda/trading/engine/
    │   ├── ExecutionEngineApplication.java
    │   ├── OrderListener.java         # @KafkaListener on orders, publishes to executions
    │   ├── MarketDataListener.java    # @KafkaListener on market-data, feeds QuoteMarket
    │   ├── QuoteMarket.java           # latest quote per symbol, marketable fill decision
    │   ├── MarketDataEvent.java       # message contract: market-data topic
    │   ├── EngineProperties.java      # engine.* settings, validated on startup
    │   ├── EngineConfig.java          # beans + topic declarations
    │   ├── OrderEvent.java            # message contract: orders topic
    │   ├── ExecutionEvent.java        # message contract: executions topic
    │   └── Side.java
    ├── main/resources/application.yml
    └── test/java/com/neueda/trading/engine/
        ├── QuoteMarketTest.java       # marketable, missing, stale and crossed quotes
        ├── MarketDataListenerTest.java
        ├── EnginePropertiesTest.java
        ├── OrderListenerTest.java     # listener logic with a mocked KafkaTemplate
        └── ExecutionEngineKafkaTest.java  # whole service against an in-process Kafka
```

## Quote-driven fills

The engine does not invent prices. `MarketDataListener` reads the
`market-data` topic, and `QuoteMarket` keeps the newest two-sided quote per
symbol, stamped with the time the engine received it. A quote without a valid
bid and ask is ignored, and the previous quote is kept.

An order fills in full only when its symbol has a fresh quote that is
marketable against its limit:

- **BUY** fills at the `ask` when `ask <= limit`.
- **SELL** fills at the `bid` when `bid >= limit`.

Otherwise the listener throws a `TemporaryFailureException`. The order stays
`NEW` and the retry backoff tries again. A missing or stale quote is handled
the same way.

Staleness is measured from when the engine received the quote, not from
`asOf`, so a publisher outage is detected. A quote that keeps arriving while
its market is closed still fills. This is a known limitation.

`QuoteMarket` takes its clock through the constructor, so tests can age quotes
without sleeping.

## Reliability

The listener sends the fill and waits for Kafka to acknowledge it before
returning, so the order's offset is only committed once its fill is safely
on `executions`. If the engine stops mid-order, it works that order again
on restart. That can produce a duplicate fill, which the order service
ignores. Unreadable messages are logged and skipped.

The listener runs one consumer thread per partition (3), so up to three
accounts' orders are worked at once.

## Configuration

| Property | Env var | Default |
|----------|---------|---------|
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` |
| `engine.quote-staleness` | `ENGINE_QUOTE_STALENESS` | `15s` |
| `engine.retry.max-retries` | `ENGINE_RETRY_MAX_RETRIES` | `60` |
| `engine.retry.initial-backoff` | `ENGINE_RETRY_INITIAL_BACKOFF` | `1s` |
| `engine.retry.backoff-multiplier` | `ENGINE_RETRY_BACKOFF_MULTIPLIER` | `2.0` |
| `engine.retry.max-backoff` | `ENGINE_RETRY_MAX_BACKOFF` | `5s` |
| `server.port` | `SERVER_PORT` | `8082` |

In `docker-compose.yml` the engine talks to `kafka:29092` and uses the
defaults above. Fills need a live quote, so start `market-data-publisher`
alongside the engine. Without quotes, orders stay `NEW`. With the defaults,
an unfilled order is retried for roughly five minutes (60 retries, backoff
capped at 5s) before it goes to `orders-dlq`.

## Building and running

```bash
cd execution-engine
mvn test               # unit tests + an in-process Kafka test; no Docker needed
mvn spring-boot:run    # against Kafka on localhost:9094 (docker-compose up -d kafka)
```

Through Docker, it runs as the `execution-engine` service:

```bash
docker-compose up --build -d execution-engine
docker-compose logs -f execution-engine
```
