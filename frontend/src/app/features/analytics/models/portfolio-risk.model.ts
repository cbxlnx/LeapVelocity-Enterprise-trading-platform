import { PerformancePeriod } from './portfolio-performance.model';
import { PortfolioSummary } from './portfolio-summary.model';

export type RiskStatus = 'READY' | 'PARTIAL' | 'EMPTY' | 'UNAVAILABLE';
export type RiskResultStatus = 'READY' | 'EMPTY' | 'UNAVAILABLE' | 'NOT_IMPLEMENTED';
export type RiskUnit = 'PERCENT' | 'DIMENSIONLESS';
export type RiskVarMethod = 'HISTORICAL' | 'PARAMETRIC' | 'MONTE_CARLO';
export type RiskIssueCode = 'EMPTY_PORTFOLIO' | 'UNSUPPORTED_POSITION' | 'UNSUPPORTED_CURRENCY' | 'INVALID_POSITION'
  | 'MISSING_INSTRUMENT_METADATA' | 'MISSING_PRICE' | 'INVALID_PRICE' | 'NO_COMMON_DATES' | 'STALE_PRICE'
  | 'INCOMPLETE_HISTORY' | 'INSUFFICIENT_HISTORY' | 'MISSING_BENCHMARK' | 'ZERO_BENCHMARK_VARIANCE'
  | 'TRADING_CALENDAR_UNAVAILABLE' | 'METHOD_NOT_IMPLEMENTED' | 'NUMERICAL_ERROR';

export interface RiskParameters {
  currency: string;
  period: PerformancePeriod;
  /** Fractions: 0.95 means 95%; confidence and horizon apply to all VaR methods. */
  confidenceLevel: number;
  holdingPeriodDays: number;
  benchmark: string;
  maxPriceAgeDays: number;
}

export type RiskVarOptions = Pick<RiskParameters, 'confidenceLevel' | 'holdingPeriodDays'>;

export interface RiskScalarMetric {
  status: RiskResultStatus;
  /** PERCENT values are percentage points (2 means 2%); beta is dimensionless. Null never means zero. */
  value: number | null;
  unit: RiskUnit;
  observations: number;
  requiredObservations: number;
  /** Inclusive price dates bounding this metric's actual return intervals. */
  from: string | null;
  to: string | null;
  reasons: RiskIssueCode[];
}

export interface RiskVarResult {
  method: RiskVarMethod;
  status: RiskResultStatus;
  estimate: boolean;
  lossPercent: number | null;
  lossAmount: number | null;
  observations: number;
  requiredObservations: number;
  from: string | null;
  to: string | null;
  reasons: RiskIssueCode[];
}

export interface RiskIssue {
  code: RiskIssueCode;
  severity: 'INFO' | 'WARNING' | 'ERROR';
  scope: string;
  symbol: string | null;
  date: string | null;
  message: string;
}

/** KPI response with independent availability per method. Dates remain calendar-date strings. */
export interface PortfolioRisk {
  schemaVersion: string;
  calculatedAt: string;
  calculationDate: string;
  parameters: RiskParameters;
  riskStatus: RiskStatus;
  valuation: PortfolioSummary;
  history: {
    requestedFrom: string | null; requestedTo: string | null; from: string | null; to: string | null;
    priceObservations: number; returnObservations: number; benchmarkReturnObservations: number;
  };
  assumptions: {
    returnModel: string; cashTreatment: string; missingPricePolicy: string; tradingCalendar: string;
    annualisationFactor: number; minimumCoreReturns: number; minimumVarObservations: number;
    minimumVarTailObservations: number; quantileMethod: string; varHorizonMethod: string;
    normalVarDistribution: string; normalVarHorizonMethod: string;
    monteCarloSimulations: number; monteCarloSeed: number;
  };
  metrics: { annualisedVolatility: RiskScalarMetric; maximumDrawdown: RiskScalarMetric; beta: RiskScalarMetric; };
  valueAtRisk: RiskVarResult[];
  issues: RiskIssue[];
  /** Validated observations from the same snapshot as the metrics; cash excluded. */
  series: {
    status: RiskResultStatus;
    wealth: Array<{ date: string; value: number }>;
    returns: Array<{ from: string; to: string; value: number }>;
    reasons: RiskIssueCode[];
  };
}
