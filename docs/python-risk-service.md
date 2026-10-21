# Python risk service implementation report

This report records the initial service integration. The subsequent
[risk analytics extension](risk-analytics-extension.md) adds the Monte Carlo
histogram, Expected Shortfall and authoritative custom-shock API and contains the
current verification results.

Implemented on `feat/py-lib-serv` after comparing it with `feat/py-serv` at
`684c08c`. No branch switch, commit, merge or rebase was performed.

## Reuse and calculation changes

Selected reference code supplies the FastAPI adapter, strict Pydantic models,
decimal transport/rounding helpers, versioned schemas, Java snapshot preparation,
authenticated HTTP client, existing-dashboard presentation adapters and tests.
The reference already uses NumPy/SciPy for most statistics. Those working library
implementations are retained; its partition-based quantile selection and NumPy
running-peak expression are adapted as follows:

| Calculation | Implementation |
| --- | --- |
| Historical VaR | `numpy.quantile(..., method="inverted_cdf")` over signed losses; clipped to a non-negative loss |
| Parametric VaR | `scipy.stats.norm.ppf`, NumPy mean and sample deviation fitted to compounded horizon returns |
| Monte Carlo VaR | Request-local `numpy.random.Generator(PCG64(20261020))`, 100,000 normal draws, same nearest-rank quantile |
| Monte Carlo histogram / ES | Same draws; NumPy histogram and empirical integrated-quantile tail average |
| Annualised volatility | `numpy.std(..., ddof=1) * sqrt(252) * 100` |
| Beta | `numpy.cov(..., ddof=1)` divided by benchmark sample variance |
| Maximum drawdown | Dated Pandas `Series`, `cummax(skipna=False)` and maximum peak-to-trough percentage loss |
| Horizon returns | NumPy sliding windows, vectorized `log1p`/`expm1` compounding, with explicit total-loss handling |

NumPy's default quantile interpolates, which would change the agreed definition.
The adapter calculates `ceil(exactDecimalConfidence * n) - 1` using `Fraction`,
then passes the midpoint of that empirical CDF bin to `numpy.quantile` with
`inverted_cdf`. This preserves even confidence epsilons below float64 resolution.
See the [NumPy quantile documentation](https://numpy.org/doc/stable/reference/generated/numpy.quantile.html).
Pandas uses the supplied validated wealth series, including the initial peak;
it never resamples or fills observations. See
[Pandas cummax](https://pandas.pydata.org/docs/reference/api/pandas.Series.cummax.html).
The normal model's inverse CDF follows
[SciPy norm](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.norm.html).

Financial definitions are preserved: current-allocation buy-and-hold weights,
cash excluded from risk, adjusted prices for historical returns, raw closes for
current valuation, exact date-interval matching for beta, 252-session annualisation,
and overlapping compounded holding-period observations rather than square-root
VaR scaling. The API supports confidence 0.90–0.99 and horizons 1–10 sessions.
Core metrics need at least 60 returns. All VaR models need
`max(100, ceil(5 / (1-confidence)))` complete horizon windows.

Application rules remain custom: Java account/holding selection, portfolio weights,
price validation, freshness, calendar coverage, lookback/benchmark alignment and
eligibility; Python interval/wealth consistency checks, numerical-range checks,
exact confidence rank policy, explicit unavailable states and decimal half-up
rounding. USD amounts (2 places) and percentages (8 places) round independently
from unrounded losses. Float64-unrepresentable inputs report `NUMERICAL_ERROR`.
There is no fallback statistical engine. Frozen Java calculators live only under
test sources as independent comparisons, not production calculations.

## Backend integration and changed files

The reference risk route `GET /api/v1/accounts/{id}/analytics/risk` is reused. It is
the only new public endpoint relative to the starting branch; existing summary
and performance routes cannot return configurable risk results. Existing JWT
ownership/admin checks and error envelopes remain authoritative. Summary and
performance paths and response shapes are preserved. Portfolio snapshot code is
extracted from the existing valuation service and continues using its repositories.
The calculation HTTP call runs after the repeatable-read snapshot transaction
completes, with a shared service token and bounded connect/complete-response timeouts.

Changes are restricted to these areas:

- `analysis/risk_service/`, its requirements, Dockerfile, README and risk tests.
- `backend/.../PortfolioAnalyticsController`, risk request/response contracts,
  risk exception handling, `PortfolioAnalyticsService`, extracted
  `PortfolioSnapshotService`, `PortfolioReturnPreparation`, shared preparation in
  `PortfolioPerformanceService`, `RiskCalculationClient/Contract`, trading calendar
  resource, risk tests and test-only reference calculators.
- `frontend/.../features/risk/` existing components, presentation utilities and
  tests; the analytics API service and risk response type. Mock sources are removed.
- `contracts/risk-calculation-v1-*.schema.json`, `.env.example`, `.gitignore`,
  Java application/test configuration, `docker-compose.yml`, the Python test stage
  in `Jenkinsfile`, isolated Docker smoke scripts and optional browser checks.

Runtime dependencies reuse installed versions: FastAPI 0.156.7, Pydantic 2.14.1,
Uvicorn 0.54.0, NumPy 2.5.4 and SciPy 1.18.1 from the reference, plus explicit
Pandas 3.0.6 (already installed through the importer environment). Development
dependencies are pytest 9.1.1 and httpx 0.28.1. Frontend `decimal.js` 10.6.0 moves
from a transitive development dependency to an explicit runtime dependency for
existing exposure presentation and waterfall offsets. Shock money arithmetic now
runs in Java. No Java dependency is added.
The importer remains separate; Python has no portfolio/market-data access.
No `.env` or real credentials were changed.

## Initial integration verification on 21 October 2026

| Check | Actual result |
| --- | --- |
| `.venv/bin/python -m pytest analysis/tests -q` | 205 passed, 25 subtests passed; 3 opt-in PostgreSQL importer tests skipped |
| `cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean test` | 520 passed; 0 failures, errors or skips, across 78 suites |
| `cd frontend && npm test -- --watch=false --coverage=false` | 137 passed across 17 files |
| `cd frontend && npm run lint` | Passed |
| `cd frontend && npm run build:prod` | Passed |
| `cd frontend && npx tsc --noEmit -p tsconfig.playwright.json` | Passed |
| `.venv/bin/python -m pip check` | Passed |
| `scripts/risk-validation/docker-smoke.sh` | Passed: analysis image built; 40 Java MVC/JWT/H2 and Python comparison tests passed, 0 failures/errors/skips; isolated containers/network/volumes removed |
| `docker compose config --quiet` | Passed |
| `docker compose up -d --build --no-deps risk-api trade-api frontend` | Built current sources and started the dashboard services; Python and frontend healthchecks passed |
| Chromium checks against `http://localhost:8085` | 7 passed, 1 skipped across the registered empty account and an existing populated account; no intercepted responses |
| `git diff --check` | Passed |

Tests cover known/golden series, all six metrics, all API holding periods, multiple
confidences, exact quantile boundaries, seeded RNG isolation and reproducibility,
rounding, insufficient/invalid/missing/stale history, mismatched dates, zero
benchmark variance, numerical overflow/underflow, JSON schema drift, real Java to
Python HTTP, repeatable-read transaction boundaries, JWT validation/ownership,
separate accounts and dashboard response mapping. Python/Java fixtures are
synthetic and isolated from the trading database.

Full Jenkins/Sonar/security scans, cross-browser checks beyond Chromium and
importer PostgreSQL tests were not run.
Existing TestClient deprecation and jsdom canvas-stub messages are non-failing.
Database contents and `.env` were not changed. Local browser verification used
short-lived diagnostic JWTs signed with the configured development key, held only
in process memory. The registered account (7) has no holdings and reports `EMPTY`;
the existing populated account (1) reports `READY` with three holdings and 250
one-year returns. Its three VaR methods and core metrics are available. The sole
browser skip is the shock calculation for the account without holdings. Java to
Python requests were also confirmed as HTTP 200 in the running calculator's logs.

## Remaining data/model gaps

All six core metrics, valuation, drawdown history, holding allocation, cash and
coverage use supported inputs. Unsupported dashboard panels now state their
missing inputs/models explicitly. Per-asset risk metrics,
covariance-based risk attribution, trusted sector
classification and historical event replay remain unimplemented. Historical
holdings/cash flows, FX and non-long-only models are also absent. These gaps
and each panel's source are listed in the [frontend guide](risk-frontend-guide.md).
Insufficient observations (especially 500 horizon windows at 99%) and dates
outside the bundled 2024–2028 calendar remain explicit unavailable results.
No live database coverage claims are made by this implementation report.

Local and Docker setup: [analysis README](../analysis/README.md).
