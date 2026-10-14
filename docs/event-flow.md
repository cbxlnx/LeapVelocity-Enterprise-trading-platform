# Event flow and Kafka contracts

trade-api, execution-engine and market-data-publisher talk only through Kafka. This file is the shared contract: field names, keys and serialization must match on both sides of every topic.

## Topics

| Topic | Key | Producer | Consumer group (consumer) | Purpose |
|---|---|---|---|---|
| `orders` | accountId | trade-api | `execution-engine` (execution-engine) | Accepted orders waiting for a fill |
| `trade-events` | accountId | execution-engine | `trade-api` (trade-api) | Fills to settle against the order |
| `market-data` | symbol | market-data-publisher | `execution-engine-market-data` (execution-engine), `trade-api-market-data` (trade-api) | Latest quote per symbol |
| `orders-dlq` | accountId | execution-engine | none (operators) | Orders the engine gave up on |

Serialization: keys are UTF-8 strings, values are JSON. Timestamps are ISO-8601 in UTC (for example `2026-10-14T14:30:00Z`). Prices and quantities are JSON numbers, parsed as decimals (`BigDecimal`) by every consumer.

## orders

```json
{
  "orderId": "3f1c2c1e-5b1a-4e0e-9c55-1d2f0a7b9e10",
  "accountId": 1,
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 10,
  "price": 187.25,
  "createdOn": "2026-10-14T14:30:00Z"
}
```

`price` is the limit price.

## trade-events

```json
{
  "executionId": "9b7a0e4c-2f7d-4a7e-8f0e-6c1b2d3e4f50",
  "orderId": "3f1c2c1e-5b1a-4e0e-9c55-1d2f0a7b9e10",
  "accountId": 1,
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 10,
  "price": 187.25,
  "limitPrice": 187.25,
  "venue": "SIM",
  "executedOn": "2026-10-14T14:30:02Z"
}
```

`price` is the fill price. `limitPrice` is the limit price copied from the order.

## market-data

```json
{
  "symbol": "AAPL",
  "lastPrice": 187.23,
  "bid": 187.20,
  "ask": 187.25,
  "currency": "USD",
  "asOf": "2026-10-14T14:30:00Z",
  "source": "yfinance"
}
```

Rules:

- One message per symbol per poll cycle (default every 5 seconds), keyed by `symbol`.
- Only two-sided quotes are published: `bid > 0`, `ask > 0` and `ask >= bid`. A symbol without one is skipped for that cycle.
- `asOf` is the time reported by the data source. Consumers do not use it for freshness.
- Freshness is measured from when the consumer received the message, not from `asOf`. A quote older than `engine.quote-staleness` (default 15s) counts as missing in the execution-engine.

## Execution rules

- BUY fills at `ask` only when `ask <= limit price`.
- SELL fills at `bid` only when `bid >= limit price`.
- A missing, stale or non-marketable quote is a temporary failure. The order stays NEW and is retried with exponential backoff. When the retry budget is spent, the order goes to `orders-dlq`.
- Orders for one account share a partition. An order that is waiting for a quote also holds back later orders for the same account until it fills or is dead-lettered.
- Staleness is measured from receipt, so a publisher outage is detected. A quote that is still flowing but describes a closed market still fills. This is a known limitation.
