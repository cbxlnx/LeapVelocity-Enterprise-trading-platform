import { describe, expect, it } from 'vitest';
import { RISK_DASHBOARD_SEED } from '../data/risk-dashboard.mock';
import { calculateCustomShock, calculateMaximumDrawdown, getVaRForHorizon, reconcileWaterfall } from './risk-calculations';

describe('risk calculation invariants', () => {
  const portfolio = RISK_DASHBOARD_SEED.portfolios[0];

  it('scales each VaR model by the square-root horizon', () => {
    const scaled = getVaRForHorizon(portfolio.oneDayVar, 10);
    expect(scaled.historicalUsd).toBeCloseTo(portfolio.oneDayVar.historicalUsd * Math.sqrt(10), 2);
    expect(scaled.monteCarloUsd).toBeGreaterThan(portfolio.oneDayVar.monteCarloUsd);
  });

  it('keeps allocations and volatility contributions coherent', () => {
    expect(portfolio.holdings.reduce((sum, holding) => sum + holding.allocationPercent, 0)).toBeCloseTo(100, 5);
    expect(portfolio.holdings.reduce((sum, holding) => sum + holding.volatilityContributionPercent, 0)).toBeCloseTo(100, 5);
    expect(portfolio.portfolioValueUsd).toBe(portfolio.investedEquityUsd + portfolio.cashReserveUsd);
    expect(portfolio.expectedShortfallUsd).toBeGreaterThan(portfolio.oneDayVar.monteCarloUsd);
  });

  it('derives the maximum drawdown from the visible series', () => {
    expect(calculateMaximumDrawdown(portfolio.drawdownSeries.map((point) => point.percent))).toBe(portfolio.maximumDrawdownPercent);
  });

  it('applies market plus sector shock once per holding', () => {
    const result = calculateCustomShock(portfolio.holdings, portfolio.portfolioValueUsd, {
      market: -10, Technology: -5, Financials: 0, Energy: 0, Healthcare: 0,
    });
    expect(result.lossUsd).toBe(-portfolio.holdings.reduce((sum, holding) => sum + holding.marketValue * ((-10 + (holding.sector === 'Technology' ? -5 : 0)) / 100), 0));
    expect(result.postShockEquityUsd).toBe(portfolio.portfolioValueUsd - result.lossUsd);
  });

  it('reconciles waterfall start, deltas, and end', () => {
    const waterfall = portfolio.historicalWaterfalls[0];
    expect(reconcileWaterfall(waterfall.startingEquityUsd, waterfall.deltas)).toBe(waterfall.endingEquityUsd);
  });
});
