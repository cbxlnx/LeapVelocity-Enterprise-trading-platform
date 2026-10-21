# Historical stress replay

Implemented on `feat/py-lib-serv` from the clean baseline `a900611`. No branch
switch, commit, merge or rebase was performed. No dependency or database schema
change is needed. Scenario calculations never write orders, positions, cash or
prices. Historical prices were populated separately through the existing importer.

The dashboard subsequently added automatic account-specific coverage checks and
removed unsupported feature placeholders. See the [current dashboard cleanup
report](risk-dashboard-cleanup.md). The event method, API validation and original
verification record below remain applicable; unsupported events are now hidden
from the normal dashboard instead of presented as pre-run or unavailable cards.

## Root causes and implementation

Historical scenarios previously consisted of static `NOT_IMPLEMENTED` metadata.
There was no replay calculation, reviewed issuer/share-class mapping, event-price
read path or Run control. Only recent prices had been loaded. The daily risk
calendar covers 2024–2028 and therefore could not validate historical daily paths.

The custom scenario already had an authenticated Java route, correct rounded
holding P&L and frontend Run/Reset controls. Its initial "no result yet" message
correctly required a Run. A separate problem hid the entire custom scenario when
the main risk/Python request failed. Both historical and custom scenario controls
now remain available through their own Java routes even in that error state.
They obtain fresh valuations when run, without requiring a successful VaR request.

## Event definitions and return method

| Scenario ID | Price endpoints (inclusive) | Definition |
| --- | --- | --- |
| `CRISIS_2008` | 2007-12-31 → 2008-12-31 | Cumulative return over the 2008 calendar year |
| `MARCH_2020` | 2020-02-28 → 2020-03-31 | Cumulative return over the March 2020 calendar month |

These are explicit calendar-period scenarios, not retrospectively selected
peak-to-trough losses or the worst single day. They include any recovery within
the window. The price observations are source-supplied trading dates, and both
exact dates must exist. The engine does not fill, interpolate or choose nearby
dates. Intermediate sessions are not required inputs to this endpoint method.
The actual imports contain the full event-period daily observations.

Three distinct methods should not be confused:

| Method | Behavior | Implemented here |
| --- | --- | --- |
| Single-day historical shock | One session's return applied to present holdings | No |
| Cumulative event return | Adjusted end/start ratio applied once to current market value | Yes |
| Daily exposure replay | Revalue holdings through every session with an explicit exposure/rebalancing policy | No |

For each validated security, Java computes:

```text
event return = adjusted end close / adjusted start close - 1
holding P&L = half-up round(current market value × event return, 2)
portfolio P&L = sum(rounded holding P&L)
post-replay holding value = current market value + holding P&L
post-replay equity = current total equity + portfolio P&L
impact percent = 100 × portfolio P&L / current total equity
```

The ratio uses `MathContext.DECIMAL128`. Percentages round independently to eight
places; holding money rounds to cents before aggregation. Cash stays unchanged.
The same `PriceSensitivity` monetary rules are reused by uniform custom shocks.
No historical share count, dividend, split or proxy multiplier is applied again.
The result is historical-return sensitivity of today's portfolio, not the user's
actual historical account performance, a forecast, or a probabilistic estimate.

This method does not need an expanded daily trading calendar. Exact source
endpoint validation is independent of `UsEquityTradingCalendar`; the original
calendar and all daily VaR validation remain unchanged. A future daily-path replay
would need an appropriate historical calendar and complete daily observations.

## Data and identity validation

Prices come from the existing yfinance importer and `analytics.daily_prices`,
using `adjusted_close` rather than the raw `close` field. Importer validation
continues to reject invalid bars, missing adjusted values, duplicate dates,
non-positive/out-of-range prices, invalid volume and date/timezone problems.
No market-data request happens inside the risk calculator or replay HTTP request.

The repository reads exact endpoint observations with `source` and `loaded_at`.
Both endpoints must have `source=yfinance`, valid adjusted prices and identical
non-null ingestion timestamps for that security. This ensures both prices came
from the same importer transaction/adjustment vintage. Mixed batches are rejected
with a re-import instruction. Endpoint values and load timestamps are included in
the response so a reported return can be reproduced even if the provider later
restates history. This validates provenance under the existing importer trust
model; it is not an independent market-data audit or a data-vendor SLA.

The versioned resource `backend/src/main/resources/risk/replay-identities-v1.json`
records the twelve curated instruments' reviewed security descriptions, accepted
current instrument names, class/currency, earliest reviewed coverage, references
and corporate-action notes. Current metadata must match the reviewed binding.
Identical ticker strings alone never qualify an instrument. New, renamed,
unreviewed or delisted instruments are unavailable until their security lineage
is reviewed. The catalog is a bounded application security review, not a general
security master or an automatic issuer-resolution service.

Examples of explicit continuity evidence:

- Google Class A changed from `GOOG` to `GOOGL` in 2014. The later Class C `GOOG`
  security is not substituted. The 2015 Alphabet reorganization converted each
  class into its corresponding class. The provider's canonical Class A history
  is requested as `GOOGL`. [Nasdaq corporate action](https://www.nasdaqtrader.com/TraderNews.aspx?id=ETA2014-24),
  [Alphabet reorganization](https://www.sec.gov/Archives/edgar/data/1652044/000119312515336577/d82837d8k12b.htm).
- QQQ traded as `QQQQ` during 2008. The exchange documents the 2011 ticker change;
  Invesco documents its 2025 UIT-to-open-end conversion. The same fund's adjusted
  history is used, not Nasdaq-100 index returns. [Exchange ticker notice](https://nasdaqtrader.com/TraderNews.aspx?id=ETA2011-21),
  [issuer conversion announcement](https://www.invesco.com/us-rest/contentdetail?contentId=13a2a8ea-b6e1-490e-a15b-1103475d503c&dnsName=us).
- XOM's 2026 holding-company reorganization exchanged each outstanding common
  share one-for-one. Both the legacy seed instrument name and the documented
  successor are explicitly bound. [Issuer filing](https://investor.exxonmobil.com/sec-filings/all-sec-filings/content/0001193125-26-291990/d71068d8k12b.htm).
- TSLA's public common shares began trading in June 2010. Its 2008 result remains
  unavailable even if erroneous/backfilled rows are supplied; no alternative
  security or index proxy is used. [Tesla IPO announcement](https://ir.tesla.com/press-release/tesla-announces-pricing-initial-public-offering).

The catalog includes the issuer references for AAPL, MSFT, AMZN, NVDA, JPM, JNJ,
WMT and SPY, including split/distribution information where relevant. Provider
adjusted history handles corporate actions; the application does not double
adjust current holdings. Review date and catalog version accompany every replay.

Any missing mapping, metadata mismatch, unsupported position/currency, missing or
duplicate endpoint, invalid source/price or mixed ingestion batch makes the whole
portfolio `UNAVAILABLE` with null totals. No holding is silently omitted. The
response exposes per-holding status/reasons and validated/expected holding counts;
individually validated rows may be present but do not constitute a portfolio total
when another holding is unavailable. Cash-only accounts report `EMPTY`, not a
calculated zero loss. Existing current-valuation freshness/long-only/USD checks
are reused before historical data is read.

## Routes and frontend

- Reused: authenticated account ownership/admin policy, existing analytics
  controller/service, portfolio snapshot validation, holdings and price repositories.
- Reused: `POST /api/v1/accounts/{id}/analytics/risk/shock` for custom price changes
  from -50% to +50%, with Run, loading, validation/error/unavailable, success and
  Reset states. No frontend financial calculator is introduced.
- Added: `POST /api/v1/accounts/{id}/analytics/risk/replay/{scenarioId}`. This is a
  read-only calculation; unknown scenarios use `VAL-422`, authorization uses the
  existing 401/403 policy, and infrastructure failures retain the error envelope.
  Existing routes cannot express event selection and its price/identity metadata.
- Extended: existing risk `stressCapabilities` now describes implemented replays
  as not yet assessed for the account. That is deliberately not `READY`. Run
  validates the account and returns actual `READY`, `EMPTY` or `UNAVAILABLE` status.
- `PortfolioReplayDto` returns event dates, methodology, price source/field, review
  version/date, current valuation, coverage counts, holding observations, identity
  evidence, P&L, totals and limitations. Existing VaR/ES contracts are unchanged.
- The existing scenario card has Run controls, error/unavailable/success states,
  expandable identity/observation details, and event/coverage metadata. Successful
  results populate the shared backend-driven holding table and waterfall. The
  selected custom result remains a separate scenario and can run independently.

## Populating prices on another installation

No new API credentials or subscription is required for the existing public
yfinance path. It requires network access to the provider, which may throttle,
fail or revise data. The importer reports failures and never manufactures prices.
Use the existing configured PostgreSQL database and Python environment:

```sh
# Current valuations and normal daily risk history (if not already populated).
.venv/bin/python -m analysis.scripts.import_data --years 2

# 2008: TSLA is deliberately excluded because it did not trade in 2008.
.venv/bin/python -m analysis.scripts.import_data \
  --start 2007-12-31 --end 2009-01-01 \
  --symbols AAPL MSFT GOOGL AMZN NVDA JPM JNJ XOM WMT SPY QQQ

# March 2020: all current curated instruments had listed shares.
.venv/bin/python -m analysis.scripts.import_data --start 2020-02-28 --end 2020-04-01
```

The importer end date is exclusive. Re-import a whole event window together,
not one endpoint, when correcting an adjustment-batch mismatch. These operations
populate stored market observations only. They do not create positions or users.
Docker users can pass the same arguments to the existing `analytics-import`
service. A normal two-year import does not automatically fetch stress periods.

## Verification on 21 October 2026

| Check | Actual result |
| --- | --- |
| Existing importer, two-year history | 12 symbols, 6,012 observations; exit 0 |
| Existing importer, 2008 event | 11 symbols, 2,794 observations; exit 0 |
| Existing importer, March 2020 event | 12 symbols, 276 observations; exit 0 |
| `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean test` in `backend` | 562 passed across 79 suites; no failures/errors/skips |
| `.venv/bin/python -m pytest analysis/tests -q` | 232 passed, 25 subtests passed; 3 opt-in importer PostgreSQL tests skipped |
| `npm test -- --watch=false --coverage=false` in `frontend` | 134 passed across 17 files |
| Frontend lint, production build, Playwright TypeScript check | Passed |
| `scripts/risk-validation/docker-smoke.sh` | 64 passed: 41 MVC/JWT/H2 checks and 23 Java/Python metric comparisons; isolated test resources cleaned up |
| Real Chromium checks against rebuilt local deployment | 10 passed, no skips, across two existing portfolios |
| Actual replay API checks | Both events returned `READY` for both portfolios; all holding totals and cash reconciled using decimal arithmetic |
| `git diff --check` | Passed |

Live provider data, not the synthetic test fixtures, supplied the successful local
replays. For local account 1, the observed portfolio P&Ls were -$33,653.83 for 2008
and -$5,333.37 for March 2020. For account 2 they were -$37,897.46 and -$3,459.17.
These are results on those current local holdings/valuation snapshots, not generic
event losses or forecasts. All four responses had three validated holdings.
Short-lived diagnostic JWTs were kept in process memory for read-only checks.
No credentials, users, positions, balances or orders were modified.

Tests cover window selection, endpoint method outside the daily calendar,
adjusted/raw price distinctions, missing/duplicate/invalid prices, source and
batch provenance, unresolved identity and same-ticker mismatches, documented
renames, post-event IPOs, omission protection, stale/empty valuations, rounding,
cash, account ownership and UI state transitions. Synthetic fixtures establish
algorithm behavior only; they are not represented as evidence of historical data.

Full CI/Sonar/security scans, browsers other than Chromium and the opt-in importer
PostgreSQL test suite were not run. Existing TestClient, jsdom canvas, yfinance
timestamp and Mockito agent warnings are non-failing. Scenario availability still
depends on complete reviewed identities and actual endpoint data. TSLA in 2008,
unknown/delisted/unreviewed securities, missing observations and unsupported
currencies/positions remain explicitly unavailable. Sector classification remains
outside this task.

## Files changed

| Files | Purpose |
| --- | --- |
| `HistoricalReplayCatalog.java`; `resources/risk/replay-identities-v1.json` | Explicit event definitions and source-backed security bindings |
| `HistoricalReplayCalculator.java`; `PortfolioReplayDto.java` | Validated endpoint replay and traceable result contract |
| `PriceSensitivity.java`; `PortfolioAnalyticsService.java` | Share existing monetary rules, orchestrate replay, preserve custom shocks |
| `PortfolioSnapshotService.java`; `HistoricalPriceRepository.java` | Reuse account valuation transaction and read exact endpoints with provenance |
| `PortfolioAnalyticsController.java`; `PortfolioRiskDto.java` | Authenticated replay route and honest pre-run capability metadata |
| `HistoricalReplayCalculatorTest.java`; `PortfolioRiskApiDatabaseTest.java` | Pure replay fixtures, repository/API/auth tests and existing regression coverage |
| Frontend analytics model/service | Typed replay response and authenticated API call |
| Stress-scenarios component TS/HTML/CSS | Run controls, calculation states, observations and identity details |
| Custom-shock component TS/HTML; dashboard TS/HTML | Keep scenarios available independently of a failed Python/risk request |
| Risk presentation models/utilities; scenario-waterfall TS/HTML | Reuse the same backend holding result for historical/custom breakdowns |
| Risk test fixtures/dashboard specs; `e2e/risk-runtime.spec.ts` | Loading/error/unavailable/success and live-replay UI checks |
| Risk guides and this report | Update methods, setup, supported features and evidence |
