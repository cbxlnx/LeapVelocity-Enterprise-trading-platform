# Risk Dashboard integration

The existing dashboard layout uses the signed-in account from `AuthService` and
`AnalyticsApiService.getRisk`. The route uses the existing authentication guard
and bearer-token interceptor. Mock portfolios and fallback demo figures have been
removed. History, holding-period and refresh actions request a fresh assessment;
superseded requests are cancelled and stale assessments/shocks are cleared.

## Data flow and routes

```text
Risk Dashboard → authenticated Java GET /api/v1/accounts/{id}/analytics/risk
              → account ownership check
              → repeatable-read holdings/valuation/price snapshot
              → transaction completes
              → authenticated Python POST /internal/v1/risk/calculate
              → Java response with valuation, risk results and dated chart series
```

The existing summary service/repositories remain the source of portfolio value,
cash, holdings, unrealized P&L and allocation. The existing instrument endpoint
supplies names and asset classes. Summary and performance URLs are preserved.
There are no browser requests to Python, Python database access, or new holdings
and price endpoints. The risk GET is reused from `feat/py-serv`; it is new relative
to the starting `feat/py-lib-serv`, whose summary/performance APIs lack risk results.

The public response type is `PortfolioRisk` in
`frontend/src/app/features/analytics/models/portfolio-risk.model.ts`. Its shape
matches the reference branch: `schemaVersion`, calculation dates, `parameters`,
`riskStatus`, `valuation`, `history`, `assumptions`, `metrics`, `valueAtRisk`,
`issues`, and `series`, with additive `monteCarlo` and `stressCapabilities` fields.
Public decimals remain JSON numbers. Internal Java/Python
decimals are strings to retain validation and monetary rounding precision.

## Existing panels

| Dashboard feature | Source and behavior |
| --- | --- |
| Portfolio value / unrealized P&L | Existing Java valuation; total equity includes cash once |
| Three VaR cards and comparison panel | Python historical, parametric and Monte Carlo results for the requested horizon |
| Volatility, beta, maximum drawdown | Python scalar metrics with their own dates/counts/status/reasons |
| Historical drawdown chart | Java's validated wealth series, displayed relative to its running peak; aggregate maximum comes from Python |
| Largest holding, cash, coverage insights | Existing valuation and validated history metadata |
| Holding allocation / concentration | Existing Java holdings, excluding cash from the weight denominator |
| Instrument-type exposure | Existing instrument `assetClass`; never presented as sector classification |
| Custom shock, holding table and waterfall | Authenticated Java POST result; price change applied once per holding, cents rounded before summing, cash unchanged |
| Historical stress replay | Automatically checks each event using the authenticated replay route; displays only complete validated results, with View breakdown and Run replay controls |
| Monte Carlo histogram | Python's same simulation batch as VaR, aggregated into at most 40 USD loss bins; VaR and ES markers |
| Expected Shortfall | Python empirical tail integral from those losses, at the same confidence and session horizon |

The custom shock calls `POST /api/v1/accounts/{id}/analytics/risk/shock` with
`{"priceChangePercent": -10}` for a hypothetical 10% price decline. Java obtains a
fresh snapshot using existing repositories, applies the shared seven-calendar-day
freshness rule, and calculates signed P&L with `BigDecimal` half-up rounding. It
supports the project's long-only USD `EQUITY` and `FUND` holdings. Backend
`totalPnL`, `impactPercent`, `postShockEquity`, `valuation` and per-holding results
populate the impact panel, holding table and waterfall. The breakdown appears only after a valid scenario is selected or run. No frontend
price sensitivity calculator remains. `decimal.js` is still used for exposure
presentation and waterfall offsets. The shock is not a historical, beta or
probabilistic model. Input edits, Reset and refresh cancel or clear stale results.
Custom shocks and historical replay checks remain independent of a failed Python risk request; each asks Java for a fresh account valuation. A known empty or invalid valuation disables the shock inputs and Run control with a corrective explanation. Server validation still runs on every submitted shock.

Historical replay uses `POST /api/v1/accounts/{id}/analytics/risk/replay/{scenarioId}`.
`CRISIS_2008` applies the adjusted-close return from 2007-12-31 to 2008-12-31;
`MARCH_2020` uses 2020-02-28 to 2020-03-31. These are cumulative calendar-period
returns applied once to current exposures, not single-day shocks, peak-to-trough
estimates, daily exposure paths or actual account history. Exact endpoint dates,
identity evidence, price source, ingestion times and coverage accompany the result.
Unknown mappings or missing observations make the whole portfolio unavailable,
with null totals and explicit holding reasons in the API. The dashboard hides that entire event, including its Run control; if neither event qualifies, it omits the historical scenario section. HTTP failures and malformed/incomplete READY responses retain a visible error and Retry control. Initial checks do not select a breakdown or overwrite custom results. TSLA cannot replay 2008 because
its IPO occurred in 2010. See the [replay guide](historical-stress-replay.md) for
data imports, identity review, calendar scope and actual verification results.

`monteCarlo` includes its own status/reasons, simulation count, confidence, horizon,
USD loss unit, bins, VaR amount and ES amount/percentage. Negative histogram values
are gains; reported VaR/ES are floored at zero. ES integrates the empirical inverse
CDF over `(confidence, 1)` and divides by the tail probability, using fractional
weight at the boundary. Ties therefore cannot inflate the selected tail mass.
See the [analytics extension report](risk-analytics-extension.md) for examples.

Sector tabs, sector insights/shocks, unsupported per-holding beta/VaR/volatility
columns, and the allocation chart labeled as risk contribution have been removed.
Instrument metadata contains names/classes, not a validated sector classification.
The current risk contract supplies aggregate returns, not an agreed per-asset
covariance attribution consistent with the portfolio's buy-and-hold reconstruction.
Valid holding allocation and instrument-type exposure remain. Missing optional
metadata hides the type grouping while preserving the holdings table. All backend
risk, replay and custom-shock implementations, schemas and tests remain available.
See the [dashboard cleanup report](risk-dashboard-cleanup.md).

## Presentation rules

- Use each metric's `status`; retain genuine zeroes and show unavailable reasons.
- Preserve partial assessments: missing benchmark data only disables beta.
- Render only validated chart series; never fill missing prices or bridge gaps.
- VaR is a non-negative estimated loss on invested holdings. Percent values are
  percentage points; returns are fractions. Maximum drawdown is a positive loss
  magnitude, with a minus sign added only for display. Beta is dimensionless.
- Request holding-period VaR from Java; do not square-root-scale it in TypeScript.
  The API accepts 1–10 sessions; the existing selector offers 1 and 10. The old
  unsupported 30-day demo option is removed.
- Keep account checks: the component rejects a mismatched response account ID;
  Java enforces ownership using the actual JWT, with the existing admin policy.

Current-allocation buy-and-hold history is a reconstruction using today's invested
weights, not actual account performance. Historical trades/cash flows, FX, shorts
and derivatives require additional inputs/models. Confidence 99% requires 500
complete horizon windows; a two-year request may legitimately be unavailable.
The bundled calendar for daily risk-return validation covers 2024–2028 and
rejects daily-history requests outside that range rather than extrapolating.
Historical cumulative endpoint replay validates exact provider observations
independently and does not use that daily calendar.

Opt-in real-deployment browser checks are available without response interception:

```sh
cd frontend
RISK_RUNTIME_URL=http://localhost:8085 RISK_RUNTIME_TOKEN='<short-lived access token>' \
  npx playwright test -c playwright.runtime.config.ts
```

They skip without a token. See [runtime setup](../analysis/README.md) and the
[calculation/verification report](python-risk-service.md).
