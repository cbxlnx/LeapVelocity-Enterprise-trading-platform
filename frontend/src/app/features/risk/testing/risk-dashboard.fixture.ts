/** Deterministic synthetic API data for tests only. Never imported by production. */
import { Instrument } from '../../../models/api.models';
import { PerformancePeriod } from '../../analytics/models/portfolio-performance.model';
import { PortfolioRisk, RiskScalarMetric } from '../../analytics/models/portfolio-risk.model';

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
    series: { status: 'READY', wealth: levels.map((value, index) => ({ date: date(index), value })),
      returns: levels.slice(1).map((value, index) => ({ from: date(index), to: date(index + 1), value: value / levels[index] - 1 })), reasons: [] },
  };
}
