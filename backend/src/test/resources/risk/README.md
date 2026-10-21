# Complete MVP fixture

`mvp-history.csv` is synthetic history, not downloaded market data. It has every
reviewed US equity session from 2025-10-16 through 2026-10-16: 252 prices and
251 one-session returns. API tests verify these dates against the session calendar.
The calculation clock is fixed at 2026-10-19T12:00:00Z in the test host only.

Raw closes stay at AAPL 100, MSFT 200 and SPY 500. Adjusted closes alternate
AAPL 100/110 and SPY 500/550; MSFT stays at 200. Quantities are 6 AAPL and 2 MSFT,
so current invested value is 1,000, cash is 500 and initial invested weights are
0.6/0.4. Keeping raw and adjusted prices distinct detects accidental valuation
from adjusted prices. Real company symbols here are only synthetic identifiers.

The expected values in `mvp-expected.json` come from this two-state path's
closed-form arithmetic, independently of the production calculator:

- Portfolio wealth alternates 1 and 1.06. There are 126 returns of 3/50 and
  125 returns of -3/53; benchmark returns are 1/10 and -1/11.
- Sample variance is `126*125/(251*250) * (3/50 + 3/53)^2`.
  Volatility is `100 * sqrt(252 * sampleVariance)` = 92.73555808%.
- Drawdown is `100 * 3/53` = 5.66037736%.
- Beta is `(3/50 + 3/53) / (1/10 + 1/11)` = 0.61078167.
- At 95% and one session, nearest rank is ceil(0.95*251) = 239. The selected
  loss is 3/53: 5.66037736% or USD 56.60 after independent rounding.

The H2 API test loads the CSV directly through SQL and exercises the real Spring
controller, transaction boundary, repositories and JWT filter. Missing benchmark,
gaps in holdings history, missing valuation prices and empty portfolios are tested
through real HTTP to a temporary local Python risk API (or the Docker smoke API).
Database state remains in H2; no market-data downloads occur. Browser tests use separate intercepted synthetic API
responses; no production fixture endpoint or backdating option is introduced.
