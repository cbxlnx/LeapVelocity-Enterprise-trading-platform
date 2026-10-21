export type RiskHorizon = 1 | 10 | 30;
export type ConcentrationView = 'stock' | 'sector';
export type ScenarioKind = 'historical' | 'custom';
export type SectorName = 'Technology' | 'Financials' | 'Energy' | 'Healthcare';

export interface HoldingRisk {
  symbol: string;
  company: string;
  sector: SectorName;
  marketValue: number;
  allocationPercent: number;
  beta: number;
  standaloneVarUsd: number;
  annualizedVolatilityPercent: number;
  volatilityContributionPercent: number;
}

export interface VaRModels {
  historicalUsd: number;
  parametricUsd: number;
  monteCarloUsd: number;
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
  sector: SectorName;
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
  lossUsd: number;
  lossPercent: number;
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
  portfolioValueUsd: number;
  oneDayVar: VaRModels;
  expectedShortfallUsd: number;
  volatilityPercent: number;
  beta: number;
  maximumDrawdownPercent: number;
  maximumDrawdownDate: string;
  dailyReturnCount: number;
  monteCarloPathCount: number;
  holdings: HoldingRisk[];
  distribution: DistributionBin[];
  drawdownSeries: DrawdownPoint[];
  insights: RiskInsight[];
  sectorExposure: SectorExposure[];
  scenarios: StressScenario[];
  historicalWaterfalls: ScenarioWaterfall[];
}

export interface RiskDashboardData {
  portfolios: RiskPortfolio[];
}

export interface ShockResult {
  lossUsd: number;
  impactPercent: number;
  postShockEquityUsd: number;
}
