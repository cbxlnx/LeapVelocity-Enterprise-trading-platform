export type PerformancePeriod = '1M' | '3M' | '6M' | '1Y' | '2Y';

export interface PerformancePoint {
  date: string;
  portfolioIndex: number;
  benchmarkIndex: number;
}

export interface PortfolioPerformance {
  accountId: number;
  period: PerformancePeriod;
  benchmark: string;
  simulationStatus: 'READY' | 'EMPTY' | 'UNAVAILABLE';
  weightingDate: string | null;
  requestedFrom: string | null;
  requestedTo: string | null;
  from: string | null;
  to: string | null;
  /** Percentage points: 2 means 2%, not 200%. */
  portfolioReturnPercent: number | null;
  benchmarkReturnPercent: number | null;
  initialWeights: { symbol: string; weight: number }[];
  points: PerformancePoint[];
  issues: string[];
}
