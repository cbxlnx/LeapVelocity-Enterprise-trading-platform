import { PortfolioRisk, PortfolioShock } from '../../analytics/models/portfolio-risk.model';

export type RiskHorizon = 1 | 10;
export type ConcentrationView = 'stock' | 'sector';
export interface HoldingExposure {
  symbol: string;
  company: string | null;
  instrumentType: string | null;
  quantity: number;
  marketValue: number | null;
  allocationPercent: number | null;
}
export interface DrawdownPoint { date: string; percent: number; }
export interface RiskInsight { label: string; value: string; detail: string; }
export interface ExposureGroup { name: string; marketValue: number; allocationPercent: number; }

/** Presentation of the authenticated snapshot, with unavailable values kept null. */
export interface RiskPortfolio {
  accountId: number;
  asOf: string | null;
  cashReserveUsd: number | null;
  investedEquityUsd: number | null;
  portfolioValueUsd: number | null;
  valuationReady: boolean;
  holdings: HoldingExposure[];
  drawdownSeries: DrawdownPoint[];
  insights: RiskInsight[];
  instrumentExposure: ExposureGroup[] | null;
  assessment: PortfolioRisk;
}
export interface ShockValues { market: number; }
export interface WaterfallDelta { label: string; deltaUsd: number; }
export interface ScenarioWaterfall {
  title: string;
  period: string;
  startingEquityUsd: number;
  deltas: WaterfallDelta[];
  endingEquityUsd: number;
  cashReserveUsd: number;
}
export interface ShockResult extends PortfolioShock {
  waterfall: ScenarioWaterfall;
}
