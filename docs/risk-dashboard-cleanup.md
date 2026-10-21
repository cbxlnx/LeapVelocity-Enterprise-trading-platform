# Risk Dashboard cleanup

Implemented on `feat/py-lib-serv` from the clean `56b1267` baseline. No branch
switch, commit, merge or rebase. Changes are confined to the Risk Dashboard,
its tests and guides. No backend route, calculation, dependency, database schema,
authentication policy or credential was changed.

## Implemented and preserved

- Historical replay automatically calls the existing authenticated, read-only
  `POST /api/v1/accounts/{id}/analytics/risk/replay/{scenarioId}` for each implemented
  event. Static capability metadata no longer appears as an unassessed card.
  A card appears only after a complete `READY` result for the requested account
  and event, with every current holding validated. View breakdown selects that
  server result; Run replay fetches a fresh valuation and recalculates it.
- An `UNAVAILABLE`, `EMPTY` or `NOT_IMPLEMENTED` replay is omitted in full. If no
  event qualifies, there is no empty historical card. A failed HTTP request or
  invalid READY response keeps a named error and Retry action. No partial totals
  are displayed. Refresh cancels pending checks; initial checks never overwrite
  a separately run custom shock.
- Both event windows were absent from the current database. The existing yfinance
  importer loaded 2,794 real observations for eleven instruments in 2008 and 276
  observations for all twelve instruments in March 2020. TSLA remains excluded
  from 2008. Existing reviewed issuer/share-class mappings and adjustment-batch
  validation are reused. Both events returned READY for local accounts 1 and 2,
  with three holdings per result. Decimal checks reconciled each rounded holding
  P&L, portfolio total, ending equity and unchanged cash. Provider revisions may
  change later imports; observations are never synthesized.
- Custom shocks retain the existing Java POST `/risk/shock`, range −50% to +50%,
  fresh authenticated valuations, cents rounded per holding, unchanged cash and
  Run/Reset. Known empty/invalid valuations disable inputs and Run with a
  corrective explanation. Failures of the unrelated Python risk request do not
  block this route or historical replay. The holding table and waterfall appear
  only for a validated selected result. Frontend coverage checks also reject
  missing, duplicate, unexpected or empty holdings in a purported READY response.
- Monte Carlo histogram and Expected Shortfall already use the same actual
  simulation sample as Monte Carlo VaR. They remain connected to Python outputs;
  confidence, horizon, tail integral and financial definitions are unchanged.
- Portfolio value, all three VaRs, volatility, beta, drawdown, actual holdings,
  allocation, cash and validated instrument-type exposure remain functional.
  Missing history or runtime failures retain relevant error/reason states.

These historical scenarios apply cumulative adjusted endpoint returns once to
current exposures. They do not represent actual historical account performance,
peak-to-trough loss, a single-day event or a daily revaluation path. See the
[method and import guide](historical-stress-replay.md) for dates and provenance.
Imports write market observations only; no users, positions, balances, orders or
instrument identities were modified.

## Removed from the dashboard

| Feature | Reason |
| --- | --- |
| Volatility-contribution panel and its allocation bar chart | No agreed per-asset attribution contract consistent with the existing reconstructed portfolio volatility; allocation remains in concentration |
| Sector tab, sector insight and sector-shock message | Current persisted instrument metadata exposes no validated sector classifications; no ticker-based assignments were inferred |
| Holding beta, VaR and volatility columns | Service exposes portfolio metrics, not validated standalone holding metrics |
| Unsupported historical event cards | Missing identities, prices, complete coverage or valid valuations cannot support a portfolio result |
| Empty scenario breakdown and duplicate pre-run custom-scenario row | No result to display before a successful calculation/selection |
| Unfinished feature notices and dead controls | Their associated unsupported panels have been removed |

Optional instrument-type grouping appears only when all metadata and invested
values support it. It is not presented as a sector or risk-attribution result.

## Still available for future work

All Java/Python calculations, replay catalog/security references, price importer,
repositories, API response types (including capability metadata), tests and
NumPy/SciPy/Pandas dependencies are retained. No backend implementation was deleted.
An unavailable event can reappear on refresh after reviewed identity coverage and
real event observations become complete. Sector analytics need a validated
classification data path; attribution/standalone holding risk need a defined
method and compatible per-asset inputs before new UI is introduced.

## Verification on 21 October 2026

| Check | Actual result |
| --- | --- |
| `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q test` in `backend` | 562 passed across 79 suites; no failures/errors/skips |
| `.venv/bin/python -m pytest analysis/tests -q` | 232 passed and 25 subtests passed; 3 opt-in importer PostgreSQL tests skipped |
| `npm test -- --watch=false --coverage=false` in `frontend` | 147 passed across 17 files |
| Frontend `npm run lint` | Passed |
| Frontend `npm run build:prod` | Passed; Angular production compilation/type checks included |
| Frontend `npx tsc --noEmit -p tsconfig.playwright.json` | Passed |
| `scripts/risk-validation/docker-smoke.sh` | 64 passed: 41 MVC/JWT/H2 API checks and 23 Java/Python comparisons; isolated resources cleaned up |
| Real Chromium against the rebuilt local frontend | 10 passed, no skips, across two existing portfolios; no response mocking |
| Real replay/custom-shock API decimal reconciliation | Passed for both accounts and both historical events; full coverage and unchanged cash |
| Existing importer, 2008 window | 11 symbols, 2,794 observations; exit 0 |
| Existing importer, March 2020 window | 12 symbols, 276 observations; exit 0 |
| `git diff --check` | Passed |

Regression coverage includes removed tabs/columns/panels, pre-selection empty
breakdown suppression, automatic replay assessment, supported/missing/empty and
unimplemented events, mixed supported/unsupported events, stale-request
cancellation, HTTP errors and Retry, mismatched accounts, incomplete/duplicate
holding coverage, custom shock availability with failed Python, invalid/empty
valuations, existing risk metrics and genuine zero Expected Shortfall.

An initial test-helper recursion and an obsolete browser assertion that clicked
the removed sector tab were corrected; the final complete runs above pass.
Existing non-failing jsdom canvas, TestClient and Mockito warnings remain. Full CI,
Sonar/security scans, browsers other than Chromium, and the opt-in importer
PostgreSQL suite were not run. Unsupported securities, absent event observations,
unsupported positions/currencies and stale valuations continue to fail validation;
no imports or UI changes relax those rules.

The updated frontend was rebuilt and is running at
[http://localhost:8085/risk](http://localhost:8085/risk). Restart the existing
application services from the repository root with:

```sh
docker compose up -d --build risk-api trade-api frontend
```

Sign in normally and open `/risk`. Historical events qualify per account and
installation. The [replay guide](historical-stress-replay.md) contains the explicit
import commands for another database. A normal two-year import excludes those
older event windows. The runtime verification used short-lived local diagnostic
JWTs held in process memory, without creating users or changing credentials.
