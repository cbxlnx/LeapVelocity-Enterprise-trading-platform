# Risk analytics and stress testing extension

The subsequent [historical replay implementation](historical-stress-replay.md)
adds validated cumulative event-return replay and independent scenario controls.
The historical limitations below describe this earlier extension's baseline.

Implemented on the existing `feat/py-lib-serv` working tree after inspecting the
clean baseline at `8a451c7`. No branch switch, commit, merge or rebase was made.
No dependency, database schema, persisted portfolio or secret changes are needed.

## Root causes and outcomes

| Feature | Root cause | Result |
| --- | --- | --- |
| Monte Carlo distribution | Simulation draws were discarded after selecting VaR; no histogram contract or chart | The same draws now supply at most 40 histogram bins and VaR/ES markers |
| Expected Shortfall | No calculation, tail convention or response fields existed | Empirical ES from the actual loss sample at the same confidence and holding period |
| Custom scenario impact | Existing frontend-only calculation had no server validation, freshness enforcement or authenticated calculation result | Read-only Java calculation using the existing account valuation snapshot |
| Holding breakdown | Waterfall received frontend deltas without a complete holding result contract | Server market value, applied shock, P&L and post-shock value drive a holding table and waterfall |
| 2008 / March 2020 replay | Stored prices inspected on 21 October 2026 span 2024-10-21 through 2026-10-20; no event-calendar validation or issuer/ticker continuity mapping exists | Both events remain explicitly `NOT_IMPLEMENTED`, with structured reasons and no fabricated loss |
| Sector increments | Instruments store symbol/name/asset class/currency, but no sector; the importer stores daily prices only | Remains `UNAVAILABLE` with a reason; no invented sectors or new schema |

The existing provider, yfinance, exposes an unstructured
[`Ticker.get_info()` dictionary](https://ranaroussi.github.io/yfinance/reference/api/yfinance.Ticker.get_info.html).
That is a possible future metadata ingestion source, not an existing validated
classification feed. Stock sector metadata and fund look-through exposure need
different treatment, a classification taxonomy, coverage checks and provenance.
Adding live metadata calls to the snapshot-only calculator would duplicate data
access and would not establish trustworthy classifications. No such calls were
added. The bundled equity calendar explicitly covers 2024–2028, so importing old
prices alone would also be insufficient to enable a validated event replay.

## Calculation definitions

The existing normal fit, compounded overlapping holding-period returns, confidence
range 90–99%, horizon range 1–10 sessions, invested-value denominator, sample-history
requirements and seeded PCG64 stream are unchanged. Every ready Monte Carlo request
produces exactly one batch of 100,000 returns. Loss fraction is the negative of the
sampled return. A loss is positive; a gain is negative. Cash is excluded.

VaR retains exact nearest-rank selection and non-negative clipping. The same raw
losses are multiplied by invested value and aggregated using
[`numpy.histogram`](https://numpy.org/doc/stable/reference/generated/numpy.histogram.html).
Bins are contiguous, ordered, at most 40, and their counts sum to the simulation
count. Bins are left-closed/right-open except for the final inclusive right edge.
A constant sample has one point bin containing every outcome. Bin bounds retain
float64 resolution rather than rounding to cents and merging small bins. The
payload contains counts, not 100,000 individual outcomes.

For ordered raw fractional losses `L[0] <= ... <= L[N-1]`, set `a = c*N`,
`k = floor(a)`, and `w = k+1-a`. ES is:

```text
max(0, (w * L[k] + sum(L[k+1:])) / (N-a))
```

This is the integral of the empirical inverse CDF over `(c, 1)`, divided by
`1-c`. Confidence arithmetic determines the boundary with exact `Fraction`
operations; NumPy computes the weighted average. Tied thresholds do not include
every tied observation or enlarge the probability mass. At 95% with 100 losses,
ES averages the five worst observations. At 95.2%, it uses the worst 4.8
observations of mass, fractionally weighting the fifth-worst observation. With
95 zeroes, four losses of 10 and one loss of 20, VaR95 is zero and ES95 is 12.
Gain-only samples report zero VaR and ES. Flat/tied tails retain their exact
sample value to avoid averaging drift at monetary rounding boundaries.

USD ES rounds to cents and percentage-point ES to eight places, independently
from the unrounded ES fraction, using the existing decimal half-up helpers. ES
is at least the corresponding reported VaR. Invalid simulations return no bins
or ES. A histogram numerical failure preserves an otherwise valid existing VaR
and marks the supplemental distribution unavailable with `NUMERICAL_ERROR`.

Custom shocks accept a finite percentage from -50 to +50 inclusive. Java obtains
the account's existing raw-close valuation under a read-only repeatable-read
transaction, applying shared currency, long-only, valuation and freshness rules
(maximum seven calendar days). Current supported price sensitivities are USD
`EQUITY` and `FUND` holdings. History, benchmark and Monte Carlo availability do
not gate this simple sensitivity.

```text
holding P&L = round_half_up(market value * priceChangePercent / 100, 2)
total P&L = sum(rounded holding P&L)
post-shock holding value = market value + rounded holding P&L
post-shock equity = existing total equity + total P&L
impact percent = 100 * total P&L / existing total equity
```

P&L is signed: negative for losses, positive for gains. Cash stays unchanged.
Two $201.50 holdings at -5% each lose $10.08, totaling $20.16; rounding the combined
unrounded loss instead would incorrectly report $20.15. Empty or unavailable
valuations return explicit statuses/reasons and null calculated amounts, with no
partial zero-valued substitutions. No orders, prices or positions are written.

## Routes and contracts

- Existing `GET /api/v1/accounts/{id}/analytics/risk` is extended additively with
  `monteCarlo` and `stressCapabilities`; all six existing metrics and fields remain.
- Existing internal `POST /internal/v1/risk/calculate` returns `monteCarlo` alongside
  unchanged metrics/VaR. Internal decimals remain strings and both Java/Python
  validate the supplemental metadata, bins, amounts and VaR relationship.
- New `POST /api/v1/accounts/{id}/analytics/risk/shock` accepts
  `{"priceChangePercent": -10}`. A separate request is necessary because Run must
  calculate the user's scenario on a fresh valuation without another simulation.
  The existing controller, JWT ownership/admin checks, service, repositories and
  error envelope are reused. Invalid input uses `VAL-422`; unavailable valuations
  use the existing explicit calculation-status convention.
- `PortfolioShockDto` includes calculation timestamps, selected shock, valuation,
  status/reasons/issues, total P&L, impact percentage, post-shock equity and holding
  rows. Frontend components display this result without recalculating holding P&L.
- JSON schemas under `contracts/` reflect the new internal response. Deploy Java
  and Python together: their internal decoder deliberately rejects schema drift.
  The frontend can still display existing KPIs if supplemental fields are absent,
  while explicitly marking the distribution unavailable.

## Changed files and purpose

Paths below are relative to their existing feature/package directories.

| Files | Purpose |
| --- | --- |
| `analysis/risk_service/calculator.py` | Retain one simulation sample, histogram aggregation, weighted ES |
| `analysis/risk_service/schemas.py`; `contracts/risk-calculation-v1-response.schema.json` | Strict distribution metadata, status, bins, ES and VaR validation |
| `analysis/tests/test_risk_distribution.py` | Controlled distributions, ties, fractional tails, constant samples, failure states, seeded draw consistency and payload bounds |
| Java `PriceShockRequestDto.java`; `PortfolioShockDto.java` | Validated user shock and authoritative result contract |
| Java `PortfolioRiskDto.java`; `RiskCalculationContract.java` | Distribution DTOs and explicit historical/sector capability reasons |
| Java `RiskCalculationClient.java` | Validate decimal transport, simulation metadata/counts/bin ordering, ES and corresponding VaR |
| Java `PortfolioSnapshotService.java`; `PortfolioAnalyticsService.java` | Reuse fresh account valuation; compute rounded holding and account sensitivity; carry distribution into risk response |
| Java `PortfolioAnalyticsController.java` | Authenticated read-only shock route and malformed-body validation envelope |
| Java `PortfolioRiskApiDatabaseTest.java`; `RiskCalculationClientTest.java`; `PortfolioAnalyticsControllerTest.java` | Risk/shock contracts, boundaries, monetary rounding, account isolation, read-only behavior and response corruption coverage |
| Frontend analytics `portfolio-risk.model.ts`; `analytics-api.service.ts` | Typed supplemental results and authenticated shock request |
| Risk `custom-shock.component.ts/.html` | Backend Run/Reset, cancellation, loading, error and unavailable states |
| Risk `distribution-drawdown.component.ts/.html`; `risk-chart-options.ts` | Histogram, VaR/ES markers, ES display, existing drawdown preserved |
| Risk `scenario-waterfall.component.ts/.html/.css` | Server holding table, cash and totals beside the existing waterfall |
| Risk `stress-scenarios.component.ts/.html`; `risk-dashboard.component.html`; `risk-dashboard.model.ts` | Share the same custom result across panels and show structured historical reasons |
| Risk `risk-calculations.ts` | Remove frontend shock calculator; map backend results to chart data only |
| Risk fixtures/specs and `frontend/e2e/risk-runtime.spec.ts` | Backend-result rendering, histogram markers, loading/errors, cancellation/reset and real deployment checks |
| `docs/risk-frontend-guide.md`; `docs/python-risk-service.md`; this report | Update integration, methodology, limitations and verification documentation |

## Verification

Verified on 21 October 2026:

| Command / check | Actual result |
| --- | --- |
| `.venv/bin/python -m pytest analysis/tests -q` | 232 passed, 25 subtests passed; 3 opt-in importer PostgreSQL tests skipped |
| `cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean test` | 544 passed across 78 suites; no failures, errors or skips |
| `cd frontend && npm test -- --watch=false --coverage=false` | 128 passed across 17 files |
| `cd frontend && npm run lint` | Passed |
| `cd frontend && npm run build:prod` | Passed locally; final source also built successfully inside the frontend Docker image |
| `cd frontend && npx tsc --noEmit -p tsconfig.playwright.json` | Passed |
| `.venv/bin/python -m pip check` | Passed; no new dependencies |
| `scripts/risk-validation/docker-smoke.sh` | 60 passed against the built Python container: 37 MVC/JWT/H2 API checks and 23 metric comparisons; test resources cleaned up |
| `docker compose up -d --build --no-deps risk-api trade-api frontend` | Current images built and started; frontend and Python healthchecks passed |
| Real-deployment Chromium checks | 8 passed, 0 skipped across accounts 7 and 1, without response interception |
| Real API boundary shocks at -50%, 0%, +50% | Both accounts returned `READY`; exact decimal holding P&L and post-shock values reconciled to account totals, including unchanged cash |
| `docker compose config --quiet`; `git diff --check` | Passed |

Both existing local accounts tested currently have three holdings and ready
40-bin simulations. Browser checks verify histogram canvas rendering, actual ES,
history/horizon refresh, backend Run results, holding tables, cash, waterfall,
authentication and account isolation. Local checks used short-lived diagnostic
JWTs held in memory; no user/password, position, price or cash changes were made.
The services remain running at `http://localhost:8085/risk`.

Initial checks caught a constant-tail floating-point rounding edge and malformed
shock JSON reaching the generic server-error handler. Both were fixed and have
regression coverage. No failing checks remain. Existing TestClient deprecation,
jsdom canvas-stub and Mockito agent messages are non-failing. Full CI/Sonar/
security scans, importer PostgreSQL tests and browsers other than Chromium were
not run.

No real event replay or sector aggregation tests are claimed: those engines are
unavailable by design pending validated inputs. Existing metric tests remain in
place; former browser-only shock calculation tests move to Java API tests, where
the calculation now lives. Frontend tests verify the returned server result and
state transitions rather than reimplementing financial arithmetic.
