/** Deterministic synthetic API data for tests only. Never imported by production. */
import { Instrument } from '../../../models/api.models';
import { PerformancePeriod } from '../../analytics/models/portfolio-performance.model';
import { HistoricalReplay, PortfolioRisk, PortfolioShock, RiskScalarMetric } from '../../analytics/models/portfolio-risk.model';

export const TEST_INSTRUMENTS: Instrument[] = [
  { id: 1, symbol: 'AAA', name: 'Synthetic Alpha', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 2, symbol: 'BBB', name: 'Synthetic Beta', assetClass: 'FUND', currency: 'USD', tradable: true },
];

export function riskFixture(accountId = 7, period: PerformancePeriod = '1Y'): PortfolioRisk {
  const first = new Date('2026-01-02T00:00:00Z');
  const date = (index: number) => new Date(first.getTime() + index * 86400000).toISOString().slice(0, 10);
  const levels = Array.from({ length: 111 }, (_, index) => [1, 1.25, 1, 1.5][index] ?? 1.35);
  const scalar = (value: number, unit: RiskScalarMetric['unit']): RiskScalarMetric => ({
    status: 'READY', value, unit, observations: 110, requiredObservations: 60, from: date(0), to: date(110), reasons: [],
  });
  return {
    schemaVersion: '1.0', calculatedAt: '2026-10-21T12:00:00Z', calculationDate: '2026-10-21',
    parameters: { currency: 'USD', period, confidenceLevel: .95, holdingPeriodDays: 1, benchmark: 'SPY', maxPriceAgeDays: 7 },
    riskStatus: 'READY',
    valuation: { accountId, currency: 'USD', asOf: '2026-10-20', valuationStatus: 'READY', holdingsValue: 1000,
      cashBalance: 100, totalEquity: 1100, costBasis: 900, unrealizedPnL: 100, issues: [],
      holdings: [
        { symbol: 'AAA', currency: 'USD', quantity: 3, averageCost: 180, price: 200, marketValue: 600, costBasis: 540, unrealizedPnL: 60, weight: .6 },
        { symbol: 'BBB', currency: 'USD', quantity: 2, averageCost: 180, price: 200, marketValue: 400, costBasis: 360, unrealizedPnL: 40, weight: .4 },
      ],
    },
    history: { requestedFrom: date(0), requestedTo: date(110), from: date(0), to: date(110), priceObservations: 111, returnObservations: 110, benchmarkReturnObservations: 110 },
    assumptions: { returnModel: 'CURRENT_ALLOCATION_BUY_AND_HOLD', cashTreatment: 'EXCLUDED', missingPricePolicy: 'REJECT_INCOMPLETE_WINDOW', tradingCalendar: 'US_EQUITY_REGULAR_SESSIONS',
      annualisationFactor: 252, minimumCoreReturns: 60, minimumVarObservations: 100, minimumVarTailObservations: 5,
      quantileMethod: 'NEAREST_RANK', varHorizonMethod: 'OVERLAPPING_COMPOUNDED_RETURNS', normalVarDistribution: 'NORMAL',
      normalVarHorizonMethod: 'FIT_TO_OVERLAPPING_COMPOUNDED_RETURNS', monteCarloSimulations: 100000, monteCarloSeed: 20261020 },
    metrics: { annualisedVolatility: scalar(15.25, 'PERCENT'), maximumDrawdown: scalar(20, 'PERCENT'), beta: scalar(.8, 'DIMENSIONLESS') },
    valueAtRisk: [
      { method: 'HISTORICAL', status: 'READY', estimate: true, lossAmount: 50, lossPercent: 5, observations: 110, requiredObservations: 100, from: date(0), to: date(110), reasons: [] },
      { method: 'PARAMETRIC', status: 'READY', estimate: true, lossAmount: 60, lossPercent: 6, observations: 110, requiredObservations: 100, from: date(0), to: date(110), reasons: [] },
      { method: 'MONTE_CARLO', status: 'READY', estimate: true, lossAmount: 55, lossPercent: 5.5, observations: 110, requiredObservations: 100, from: date(0), to: date(110), reasons: [] },
    ], issues: [],
    monteCarlo: { status: 'READY', reasons: [], simulations: 100000, confidenceLevel: .95, holdingPeriodDays: 1,
      unit: 'USD_LOSS', tailConvention: 'EMPIRICAL_INTEGRATED_QUANTILE', varAmount: 55,
      expectedShortfallAmount: 70, expectedShortfallPercent: 7,
      bins: [{ lowerBound: -100, upperBound: 0, count: 50000 }, { lowerBound: 0, upperBound: 100, count: 50000 }] },
    stressCapabilities: { historical: [{ id: 'MARCH_2020', title: 'March 2020 market stress', status: 'UNAVAILABLE',
      reasons: ['Select Run replay to validate stored observations and security identity.'] }],
      sectorIncrements: { id: 'SECTOR_INCREMENTS', title: 'Sector increments', status: 'UNAVAILABLE', reasons: ['No sector metadata.'] } },
    series: { status: 'READY', wealth: levels.map((value, index) => ({ date: date(index), value })),
      returns: levels.slice(1).map((value, index) => ({ from: date(index), to: date(index + 1), value: value / levels[index] - 1 })), reasons: [] },
  };
}

/** Fixed backend result: browser tests must not implement another shock calculator. */
export function shockFixture(accountId = 7): PortfolioShock {
  return { schemaVersion: '1.0', calculatedAt: '2026-10-21T12:00:00Z', calculationDate: '2026-10-21',
    status: 'READY', reasons: [], priceChangePercent: -10, valuation: riskFixture(accountId).valuation,
    totalPnL: -100, impactPercent: -9.09090909, postShockEquity: 1000, issues: [],
    holdings: [
      { symbol: 'AAA', marketValue: 600, priceChangePercent: -10, pnl: -60, postShockValue: 540 },
      { symbol: 'BBB', marketValue: 400, priceChangePercent: -10, pnl: -40, postShockValue: 360 },
    ] };
}

/** Synthetic contract fixture; not evidence of real historical observations. */
export function replayFixture(accountId = 7): HistoricalReplay {
  const shock = shockFixture(accountId);
  return { schemaVersion: '1.0', calculatedAt: shock.calculatedAt, calculationDate: shock.calculationDate,
    status: 'READY', reasons: [], scenario: { id: 'MARCH_2020', title: 'March 2020 market stress', from: '2020-02-28', to: '2020-03-31' },
    methodology: 'CUMULATIVE_ADJUSTED_CLOSE_RETURN_ON_CURRENT_EXPOSURES', priceSource: 'synthetic-test-fixture',
    priceField: 'adjusted_close', identityReviewVersion: 'TEST_ONLY', identityReviewedOn: '2026-10-21',
    valuation: shock.valuation, expectedHoldings: 2, validatedHoldings: 2,
    totalPnL: shock.totalPnL, impactPercent: shock.impactPercent, postShockEquity: shock.postShockEquity,
    holdings: shock.holdings.map(row => ({ ...row, historicalTicker: row.symbol, providerSymbol: row.symbol,
      securityIdentity: 'Synthetic fixture identity', identitySources: [], identityNotes: 'Fixture only',
      status: 'READY', reasons: [], fromAdjustedClose: 100, toAdjustedClose: 90, loadedAt: '2026-10-21T10:00:00Z' })),
    limitations: ['Synthetic contract fixture, not production historical data.'] };
}
