export type RiskHorizon = 1 | 10 | 30;
export type HistoricalLookback = '1Y' | '3Y' | '5Y' | '10Y' | 'MAX';
export type ConcentrationView = 'stock' | 'sector';
export type ScenarioKind = 'historical' | 'custom';
export type SectorName = 'Technology' | 'Financials' | 'Energy' | 'Healthcare' | 'Other';

export interface SectionStatus {
  status: 'READY' | 'PARTIAL' | 'EMPTY' | 'UNAVAILABLE';
  issues: string[];
  observations?: number | null;
  requiredObservations?: number | null;
}

export interface RiskCoverage {
  coveredSymbols: string[];
  missingSymbols: string[];
  observedFrom?: string | null;
  observedTo?: string | null;
}

export interface HoldingRisk {
  symbol: string;
  company: string;
  sector: SectorName | null;
  marketValue: number | null;
  allocationPercent: number | null;
  beta: number | null;
  standaloneVarUsd: number | null;
  annualizedVolatilityPercent: number | null;
  volatilityContributionPercent: number | null;
}

export interface VaRModels {
  historicalUsd: number | null;
  parametricUsd: number | null;
  monteCarloUsd: number | null;
}

export interface DrawdownPoint {
  date: string;
  percent: number;
}

export interface DistributionBin {
  returnUsd: number;
  frequency: number;
}

export interface RiskInsight {
  label: string;
  value: string;
  detail: string;
}

export interface SectorExposure {
  sector: SectorName | string;
  marketValue: number;
  allocationPercent: number;
  symbols: string[];
}

export interface ShockValues {
  market: number;
  Technology: number;
  Financials: number;
  Energy: number;
  Healthcare: number;
}

export interface WaterfallDelta {
  label: string;
  deltaUsd: number;
}

export interface ScenarioWaterfall {
  title: string;
  period: string;
  startingEquityUsd: number;
  deltas: WaterfallDelta[];
  endingEquityUsd: number;
  cashReserveUsd: number;
}

export interface StressScenario {
  id: string;
  title: string;
  period: string;
  description: string;
  kind: ScenarioKind;
  lossUsd: number | null;
  lossPercent: number | null;
  waterfall?: ScenarioWaterfall;
}

export interface RiskPortfolio {
  accountId: string;
  accountLabel: string;
  asOf: string;
  historicalFrom: string;
  historicalTo: string;
  cashReserveUsd: number;
  investedEquityUsd: number;
  portfolioValueUsd: number | null;
  oneDayVar: VaRModels;
  varByHorizon?: Partial<Record<RiskHorizon, VaRModels>>;
  expectedShortfallUsd: number | null;
  volatilityPercent: number | null;
  beta: number | null;
  maximumDrawdownPercent: number | null;
  maximumDrawdownDate: string | null;
  dailyReturnCount: number;
  monteCarloPathCount: number;
  holdings: HoldingRisk[];
  distribution: DistributionBin[];
  drawdownSeries: DrawdownPoint[];
  insights: RiskInsight[];
  sectorExposure: SectorExposure[];
  scenarios: StressScenario[];
  historicalWaterfalls: ScenarioWaterfall[];
  sectionStatus?: Record<string, SectionStatus>;
  coverage?: RiskCoverage;
  issues?: string[];
}

export interface RiskDashboardData {
  portfolios: RiskPortfolio[];
}

export interface ShockResult {
  lossUsd: number;
  impactPercent: number;
  postShockEquityUsd: number;
  waterfall?: ScenarioWaterfall;
}
