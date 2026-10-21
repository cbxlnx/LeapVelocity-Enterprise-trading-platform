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
| Historical stress replay / stress alert | Unavailable: no validated event histories and replay model |
| Sector exposure / sector shocks | Unavailable: no trusted sector classification |
| Per-holding beta/volatility/VaR and volatility contribution | Unavailable: no per-asset result contract or attribution model |
| Monte Carlo histogram | Python's same simulation batch as VaR, aggregated into at most 40 USD loss bins; VaR and ES markers |
| Expected Shortfall | Python empirical tail integral from those losses, at the same confidence and session horizon |

The custom shock calls `POST /api/v1/accounts/{id}/analytics/risk/shock` with
`{"priceChangePercent": -10}` for a hypothetical 10% price decline. Java obtains a
fresh snapshot using existing repositories, applies the shared seven-calendar-day
freshness rule, and calculates signed P&L with `BigDecimal` half-up rounding. It
supports the project's long-only USD `EQUITY` and `FUND` holdings. Backend
`totalPnL`, `impactPercent`, `postShockEquity`, `valuation` and per-holding results
populate the impact panel, scenario row, holding table and waterfall. No frontend
price sensitivity calculator remains. `decimal.js` is still used for exposure
presentation and waterfall offsets. The shock is not a historical, beta or
probabilistic model. Input edits, Reset and refresh cancel or clear stale results.

`monteCarlo` includes its own status/reasons, simulation count, confidence, horizon,
USD loss unit, bins, VaR amount and ES amount/percentage. Negative histogram values
are gains; reported VaR/ES are floored at zero. ES integrates the empirical inverse
CDF over `(confidence, 1)` and divides by the tail probability, using fractional
weight at the boundary. Ties therefore cannot inflate the selected tail mass.
See the [analytics extension report](risk-analytics-extension.md) for examples.

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
The bundled US equity session calendar covers 2024–2028 and rejects requests
outside that range rather than extrapolating.

Opt-in real-deployment browser checks are available without response interception:

```sh
cd frontend
RISK_RUNTIME_URL=http://localhost:8085 RISK_RUNTIME_TOKEN='<short-lived access token>' \
  npx playwright test -c playwright.runtime.config.ts
```

They skip without a token. See [runtime setup](../analysis/README.md) and the
[calculation/verification report](python-risk-service.md).
