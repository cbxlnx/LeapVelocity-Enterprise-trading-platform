# LeapVelocity Risk Service

The risk service is a read-only FastAPI microservice that calculates portfolio risk metrics for the Angular risk dashboard. It reads the signed-in account's current holdings from `trade-api` and historical adjusted-close prices from PostgreSQL `analytics.daily_prices`.

It does not place orders, mutate positions, publish Kafka events, or persist risk snapshots.

## Location

```text
risk-service/
  app/
    api/risk_routes.py              HTTP endpoints
    clients/trade_api_client.py     Read-only trade API client
    core/config.py                  Environment-backed settings
    core/security.py                JWT validation and account access
    db/postgres.py                  Read-only PostgreSQL connection
    repositories/                   Historical price queries
    schemas/risk_models.py          API response models
    services/metrics.py             VaR and statistical calculations
    services/risk_engine.py         Portfolio risk orchestration
    services/stress.py              Historical and custom stress tests
  tests/
  Dockerfile
  requirements.txt
```

## Runtime Architecture

```text
Browser
  |
  | /risk-api/*
  v
Frontend Nginx :8080
  |
  | internal Docker network
  v
risk-service :8091
  |                         |
  | bearer token            | read-only transaction
  v                         v
trade-api :8081        PostgreSQL analytics.daily_prices
```

The service is not published directly to the host. In Docker Compose, the frontend proxies `/risk-api/` to `risk-service:8091`.

## API

All account endpoints require a bearer JWT. The token's numeric `accountId` must match the URL account, unless the token has an admin role.

### Health check

```http
GET /healthz
```

Response:

```json
{"status": "ok"}
```

### Risk dashboard

```http
GET /risk-api/v1/accounts/{accountId}/risk/dashboard?lookbackDays=252&confidence=0.95&benchmark=SPY&mcPaths=10000
Authorization: Bearer <access-token>
```

The response contains portfolio value, holdings, allocations, VaR by horizon, expected shortfall, volatility, beta, risk contributions, drawdown, distributions, sector exposure, historical stress scenarios, coverage, and section status.

Supported query limits:

- `lookbackDays`: 30 to 10000; default `252`
- `confidence`: 0.8 inclusive to 1.0 exclusive; default `0.95`
- `benchmark`: default `SPY`
- `mcPaths`: 100 to 100000; default `10000`
- `horizon`: `1`, `10`, or `30` (accepted for compatibility; the dashboard calculates all three horizons)

### Custom shock

```http
POST /risk-api/v1/accounts/{accountId}/risk/custom-shock
Authorization: Bearer <access-token>
Content-Type: application/json
```

Example body:

```json
{
  "market": -10,
  "Technology": -5,
  "Financials": 0,
  "Energy": 0,
  "Healthcare": 0
}
```

The response contains the calculated loss, percentage impact, post-shock equity, and a position-level waterfall. Shock values are percentages and are limited to `-100` through `100`.

## Calculations

The implementation is in `app/services/metrics.py` and is orchestrated by `app/services/risk_engine.py`.

- **Historical VaR:** empirical quantile of historical portfolio losses.
- **Parametric VaR:** $z_{confidence} \times \sigma_{daily} \times \sqrt{horizon} \times equity$.
- **Monte Carlo VaR:** correlated multivariate-normal asset-return simulations using the observed asset means and covariance matrix.
- **Expected shortfall:** mean loss at or beyond the VaR threshold. The dashboard uses one-day Monte Carlo expected shortfall.
- **Volatility:** standard deviation of portfolio daily returns annualized by $\sqrt{252}$.
- **Beta:** covariance of portfolio or instrument returns with benchmark returns divided by benchmark variance.
- **Risk contribution:** covariance-based marginal contribution multiplied by each holding's portfolio weight.
- **Drawdown:** cumulative wealth relative to its running maximum; maximum drawdown is the largest peak-to-trough decline.
- **Historical stress:** holding-level returns over the configured 2008 and 2020 event windows.
- **Custom stress:** market shock plus the matching sector shock applied to each holding's current market value.

The Monte Carlo random seeds are deterministic per horizon, which keeps test and dashboard results reproducible for the same input data.

## Data and missing-data behavior

Current account data comes from these trade API endpoints:

- `GET /api/v1/accounts/{accountId}`
- `GET /api/v1/accounts/{accountId}/positions`
- `GET /api/v1/instruments`
- `GET /api/v1/market-data/quotes?symbols=...`

Historical data is queried from `analytics.daily_prices` using adjusted close prices. Connections execute `SET TRANSACTION READ ONLY`; Compose configures the service with the dedicated `risk_readonly` database role.

The service does not replace missing values with zero. If quotes, instrument metadata, benchmark history, or enough aligned observations are missing, affected metrics are returned as `null` and the response reports `READY`, `PARTIAL`, or `UNAVAILABLE` section status with coverage and issue details.

## Configuration

The service reads these environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `RISK_JWT_SECRET` | empty | JWT HS256 verification secret |
| `RISK_JWT_ISSUER` | `leapvelocity-auth` | Required JWT issuer |
| `RISK_TRADE_API_BASE_URL` | `http://trade-api:8081/api` | Internal trade API base URL |
| `RISK_DB_NAME` | `leapvelocity_trading_db` | PostgreSQL database |
| `RISK_DB_USER` | `postgres` in the app, `risk_readonly` in Compose | PostgreSQL user |
| `RISK_DB_PASSWORD` | empty | PostgreSQL password |
| `RISK_DB_HOST` | `trade-db` | PostgreSQL host |
| `RISK_DB_PORT` | `5432` | PostgreSQL port |
| `RISK_BENCHMARK` | `SPY` | Default benchmark |
| `RISK_DEFAULT_LOOKBACK_DAYS` | `252` | Default history window |
| `RISK_DEFAULT_CONFIDENCE` | `0.95` | Default VaR confidence |
| `RISK_DEFAULT_MC_PATHS` | `10000` | Default simulation paths |
| `RISK_MINIMUM_OBSERVATIONS` | `30` | Minimum aligned daily returns |

## Run with Docker Compose

From the repository root:

```bash
docker-compose up -d --build risk-service
```

To start the complete platform:

```bash
docker-compose up -d --build
```

Check the service and health endpoint:

```bash
docker-compose ps risk-service
docker-compose logs -f risk-service
```

The browser should use the frontend URL rather than port `8091` directly:

```text
http://localhost:8085
```

## Run locally

Install the Python dependencies, then start Uvicorn from the `risk-service` directory:

```bash
python -m pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8091
```

Set `PYTHONPATH` to the service directory when invoking commands from another working directory.

## Tests

From the `risk-service` directory:

```bash
pytest -q
```

The suite covers health, JWT/account authorization, metric calculations, stress calculations, and a mocked integration contract that validates the dashboard response schema.
