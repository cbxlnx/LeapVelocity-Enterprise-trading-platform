import { describe, expect, it } from 'vitest';
import { replayFixture, riskFixture, shockFixture, TEST_INSTRUMENTS } from '../testing/risk-dashboard.fixture';
import { toReplayBreakdown, toShockResult, drawdownPoints, toRiskPortfolio } from './risk-calculations';

describe('account-specific risk presentation', () => {
  it('uses actual market values and excludes cash from concentration denominators', () => {
    const response = riskFixture();
    response.valuation.holdings[0].weight = .01; // Serialized/rounded weights must not determine exposure.
    const portfolio = toRiskPortfolio(response, TEST_INSTRUMENTS);
    expect(portfolio.holdings.map(h => h.allocationPercent)).toEqual([60, 40]);
    expect(portfolio.portfolioValueUsd).toBe(1100);
    expect(portfolio.investedEquityUsd).toBe(1000);
    expect(portfolio.instrumentExposure).toEqual([
      { name: 'EQUITY', marketValue: 600, allocationPercent: 60 },
      { name: 'FUND', marketValue: 400, allocationPercent: 40 },
    ]);
    expect(portfolio.insights[0].value).toBe('AAA · 60.00%');
  });

  it('does not infer missing metadata or sector exposure', () => {
    const portfolio = toRiskPortfolio(riskFixture(), null);
    expect(portfolio.holdings[0].company).toBeNull();
    expect(portfolio.instrumentExposure).toBeNull();
    expect(portfolio.insights.some(insight => insight.label.includes('Sector'))).toBe(false);
  });

  it('draws actual dates and keeps the initial wealth peak', () => {
    const response = riskFixture();
    const points = drawdownPoints(response);
    expect(points.map(p => p.date)).toEqual(response.series.wealth.map(p => p.date));
    expect(points.slice(0, 5).map(p => p.percent)).toEqual([0, 0, expect.closeTo(-20), 0, expect.closeTo(-10)]);
    expect(Math.min(...points.map(p => p.percent))).toBeCloseTo(-20);
    response.series.status = 'UNAVAILABLE';
    response.series.reasons = ['INCOMPLETE_HISTORY'];
    expect(drawdownPoints(response)).toEqual([]);
  });

  it.each(['STALE_PRICE', 'MISSING_PRICE'] as const)('blocks valued exposures for %s', code => {
    const response = riskFixture();
    response.issues.push({ code, scope: 'valuation', severity: 'ERROR', date: null, symbol: 'AAA', message: 'Test data issue' });
    const portfolio = toRiskPortfolio(response, TEST_INSTRUMENTS);
    expect(portfolio.holdings[0].marketValue).toBeNull();
    expect(portfolio.holdings[0].allocationPercent).toBeNull();
  });

  it('uses the backend result verbatim for holding breakdown and waterfall', () => {
    const response = shockFixture();
    const result = toShockResult(response)!;
    expect(result.totalPnL).toBe(response.totalPnL);
    expect(result.holdings).toEqual(response.holdings);
    expect(result.waterfall.deltas).toEqual([{ label: 'AAA', deltaUsd: -60 }, { label: 'BBB', deltaUsd: -40 }]);
    expect(result.waterfall.endingEquityUsd).toBe(1000);
    expect(result.waterfall.cashReserveUsd).toBe(100);
    response.status = 'UNAVAILABLE'; response.totalPnL = null;
    expect(toShockResult(response)).toBeNull();
  });

  it.each(['missing', 'duplicate', 'unexpected', 'empty'] as const)('rejects %s holding coverage even when a server response claims READY', kind => {
    const shock = shockFixture();
    const replay = replayFixture();
    for (const response of [shock, replay]) {
      if (kind === 'missing') response.holdings.pop();
      if (kind === 'duplicate') response.holdings[1].symbol = response.holdings[0].symbol;
      if (kind === 'unexpected') response.holdings[1].symbol = 'UNKNOWN';
      if (kind === 'empty') { response.holdings = []; response.valuation.holdings = []; }
    }
    expect(toShockResult(shock)).toBeNull();
    expect(toReplayBreakdown(replay)).toBeNull();
  });

  it('sums fractional-dollar exposures without binary rounding drift', () => {
    const response = riskFixture();
    response.valuation.holdings[0].marketValue = .1;
    response.valuation.holdings[1].marketValue = .2;
    response.valuation.holdingsValue = .3;
    response.valuation.cashBalance = .1;
    response.valuation.totalEquity = .4;
    const metadata = TEST_INSTRUMENTS.map(instrument => ({ ...instrument, assetClass: 'EQUITY' }));
    const portfolio = toRiskPortfolio(response, metadata);
    expect(portfolio.instrumentExposure).toEqual([{ name: 'EQUITY', marketValue: .3, allocationPercent: 100 }]);
  });
});
