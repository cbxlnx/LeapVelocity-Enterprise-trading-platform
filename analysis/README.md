# Python analysis runtime

The existing `scripts/import_data.py` is a finite yfinance/PostgreSQL batch. The
separate `risk_service` FastAPI process accepts Java-prepared portfolio snapshots.
It never imports the importer, connects to the database, or fetches market data.
Risk statistics use pinned NumPy, SciPy and Pandas packages.

## Local setup

Use Python 3.12 and Java 21. From the repository root:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r analysis/requirements-dev.txt
export RISK_SERVICE_TOKEN="$(openssl rand -hex 32)"
export RISK_SERVICE_URL=http://localhost:8000
.venv/bin/python -m uvicorn analysis.risk_service.app:app --host 127.0.0.1 --port 8000
```

Pass the same token to Java along with its existing database/JWT/Kafka settings.
Tokens must be printable ASCII without spaces. No credentials are supplied by
default. Java reads `RISK_SERVICE_URL`, `RISK_SERVICE_TOKEN`,
`RISK_SERVICE_CONNECT_TIMEOUT_MS` (default 1000, range 1–10000) and
`RISK_SERVICE_READ_TIMEOUT_MS` (default 5000, range 1–30000).
The read budget includes receipt of the complete response body; redirects are
disabled and invalid responses fail through the existing Java error envelope.

`GET /health` is unauthenticated. `POST /internal/v1/risk/calculate` requires
`X-Risk-Service-Token`. Its versioned JSON schemas are under `contracts/`.
Angular calls Java with the existing bearer JWT, never this internal endpoint.

## Docker

Set `RISK_SERVICE_TOKEN` in the root `.env` alongside existing database/JWT settings.
`risk-api` and `analytics-import` share the analysis image with separate commands.
The risk container has no published host port or database credentials. Java waits
for its healthcheck. Starting the risk process never starts an import.

```sh
docker compose up -d --build risk-api trade-api frontend
```

The importer remains a separate, explicit operation. Its normal database settings
and usage are documented in `scripts/import_data.py` and the repository README.

## Verification

```sh
.venv/bin/python -m pytest analysis/tests -q
cd backend
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean test
cd ..
scripts/risk-validation/docker-smoke.sh
```

Java tests start a temporary local Python process; `RISK_TEST_PYTHON` can select a
different interpreter. The Docker smoke builds the actual analysis image and runs
Java MVC/JWT/H2 and metric comparisons through HTTP on an isolated network. It
cleans up its own containers and volumes without using the trading database.
Three importer PostgreSQL tests are opt-in through `ANALYTICS_TEST_DB=1`.

See the [implementation report](../docs/python-risk-service.md) and
[dashboard integration guide](../docs/risk-frontend-guide.md).
Historical stress windows are imported with the same importer; see the exact
[event import commands and validation rules](../docs/historical-stress-replay.md).
