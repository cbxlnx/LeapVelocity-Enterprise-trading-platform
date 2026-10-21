import { describe, expect, it } from 'vitest';
import { riskFixture, TEST_INSTRUMENTS } from '../testing/risk-dashboard.fixture';
import { calculateCustomShock, drawdownPoints, roundCurrency, toRiskPortfolio } from './risk-calculations';

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
    expect(portfolio.insights[1].value).toBe('Unavailable');
    expect(portfolio.insights[1].detail).toContain('no sector classification');
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

  it.each(['STALE_PRICE', 'MISSING_PRICE'] as const)('blocks valued exposures and shocks for %s', code => {
    const response = riskFixture();
    response.issues.push({ code, scope: 'valuation', severity: 'ERROR', date: null, symbol: 'AAA', message: 'Test data issue' });
    const portfolio = toRiskPortfolio(response, TEST_INSTRUMENTS);
    expect(portfolio.holdings[0].marketValue).toBeNull();
    expect(portfolio.holdings[0].allocationPercent).toBeNull();
    expect(calculateCustomShock(portfolio, { market: -10 })).toBeNull();
  });

  it.each([-10, 10, 0])('applies a user price change of %s once, preserving cash and reconciling cents', market => {
    const portfolio = toRiskPortfolio(riskFixture(), TEST_INSTRUMENTS);
    const result = calculateCustomShock(portfolio, { market })!;
    expect(result.lossUsd).toBe(-1000 * market / 100);
    expect(result.postShockEquityUsd).toBe(1100 + 1000 * market / 100);
    expect(result.waterfall.cashReserveUsd).toBe(100);
    expect(result.waterfall.startingEquityUsd + result.waterfall.deltas.reduce((sum, d) => sum + d.deltaUsd, 0)).toBe(result.waterfall.endingEquityUsd);
    expect(result.impactPercent).toBeCloseTo(result.lossUsd / 1100 * 100);
  });

  it.each([NaN, Infinity, -Infinity, -51, 51])('rejects invalid user price changes: %s', market => {
    expect(calculateCustomShock(toRiskPortfolio(riskFixture(), null), { market })).toBeNull();
  });

  it.each([[10.075, 10.08], [-10.075, -10.08], [1.005, 1.01], [-1.005, -1.01]])(
    'rounds decimal half cents away from zero: %s → %s', (value, expected) => {
      expect(roundCurrency(value)).toBe(expected);
    });

  it.each([-5, 5])('keeps half-cent holding shocks symmetric at %s%%', market => {
    const portfolio = toRiskPortfolio(riskFixture(), null);
    portfolio.holdings = [{ ...portfolio.holdings[0], marketValue: 201.50 }];
    portfolio.portfolioValueUsd = 301.50;
    const result = calculateCustomShock(portfolio, { market })!;
    expect(result.waterfall.deltas[0].deltaUsd).toBe(market > 0 ? 10.08 : -10.08);
    expect(result.lossUsd).toBe(market > 0 ? -10.08 : 10.08);
    expect(result.postShockEquityUsd).toBe(market > 0 ? 311.58 : 291.42);
  });

  it('sums fractional-dollar exposures and scenario deltas without binary rounding drift', () => {
    const response = riskFixture();
    response.valuation.holdings[0].marketValue = .1;
    response.valuation.holdings[1].marketValue = .2;
    response.valuation.holdingsValue = .3;
    response.valuation.cashBalance = .1;
    response.valuation.totalEquity = .4;
    const metadata = TEST_INSTRUMENTS.map(instrument => ({ ...instrument, assetClass: 'EQUITY' }));
    const portfolio = toRiskPortfolio(response, metadata);
    expect(portfolio.instrumentExposure).toEqual([{ name: 'EQUITY', marketValue: .3, allocationPercent: 100 }]);
    const result = calculateCustomShock(portfolio, { market: 10 })!;
    expect(result.lossUsd).toBe(-.03);
    expect(result.postShockEquityUsd).toBe(.43);
    expect(result.impactPercent).toBe(-7.5);
  });

  it.each([NaN, Infinity, -Infinity])('blocks non-finite holding values: %s', marketValue => {
    const portfolio = toRiskPortfolio(riskFixture(), null);
    portfolio.holdings[0].marketValue = marketValue;
    expect(calculateCustomShock(portfolio, { market: -10 })).toBeNull();
  });
});
