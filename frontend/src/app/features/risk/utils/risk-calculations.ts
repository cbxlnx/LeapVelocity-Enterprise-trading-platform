import {
  HoldingRisk,
  RiskHorizon,
  SectorExposure,
  ShockResult,
  ShockValues,
  VaRModels,
  WaterfallDelta,
} from '../models/risk-dashboard.model';

export function calculatePortfolioValue(investedEquityUsd: number, cashReserveUsd: number): number {
  return roundCurrency(investedEquityUsd + cashReserveUsd);
}

export function calculateAllocations(values: number[]): number[] {
  const investedEquityUsd = values.reduce((total, value) => total + value, 0);
  if (investedEquityUsd === 0) return values.map(() => 0);
  return values.map((value) => (value / investedEquityUsd) * 100);
}

export function scaleVaR(oneDayLossUsd: number, horizon: RiskHorizon): number {
  return roundCurrency(oneDayLossUsd * Math.sqrt(horizon));
}

export function getVaRForHorizon(oneDayVaR: VaRModels, horizon: RiskHorizon): VaRModels {
  return {
    historicalUsd: scaleVaR(oneDayVaR.historicalUsd, horizon),
    parametricUsd: scaleVaR(oneDayVaR.parametricUsd, horizon),
    monteCarloUsd: scaleVaR(oneDayVaR.monteCarloUsd, horizon),
  };
}

export function lossPercent(lossUsd: number, portfolioValueUsd: number): number {
  return portfolioValueUsd === 0 ? 0 : (lossUsd / portfolioValueUsd) * 100;
}

export function calculateMaximumDrawdown(drawdownPercentages: number[]): number {
  return drawdownPercentages.length === 0 ? 0 : Math.min(...drawdownPercentages);
}

export function calculateRiskContributionTotal(contributions: number[]): number {
  return contributions.reduce((total, contribution) => total + contribution, 0);
}

export function calculateSectorExposure(holdings: HoldingRisk[]): SectorExposure[] {
  const grouped = new Map<string, { marketValue: number; symbols: string[] }>();
  for (const holding of holdings) {
    const current = grouped.get(holding.sector) ?? { marketValue: 0, symbols: [] };
    current.marketValue += holding.marketValue;
    current.symbols.push(holding.symbol);
    grouped.set(holding.sector, current);
  }
  const investedEquityUsd = holdings.reduce((total, holding) => total + holding.marketValue, 0);
  return [...grouped.entries()].map(([sector, value]) => ({
    sector: sector as SectorExposure['sector'],
    marketValue: roundCurrency(value.marketValue),
    allocationPercent: investedEquityUsd === 0 ? 0 : (value.marketValue / investedEquityUsd) * 100,
    symbols: value.symbols,
  }));
}

export function calculateCustomShock(
  holdings: HoldingRisk[],
  portfolioValueUsd: number,
  shocks: ShockValues,
): ShockResult {
  const lossUsd = holdings.reduce((total, holding) => {
    const combinedShockPercent = shocks.market + shocks[holding.sector];
    return total - holding.marketValue * (combinedShockPercent / 100);
  }, 0);
  const roundedLoss = roundCurrency(lossUsd);
  return {
    lossUsd: roundedLoss,
    impactPercent: lossPercent(roundedLoss, portfolioValueUsd),
    postShockEquityUsd: roundCurrency(portfolioValueUsd - roundedLoss),
  };
}

export function reconcileWaterfall(startingEquityUsd: number, deltas: WaterfallDelta[]): number {
  return roundCurrency(startingEquityUsd + deltas.reduce((total, item) => total + item.deltaUsd, 0));
}

export function stressLossFromPercent(portfolioValueUsd: number, lossPercentValue: number): number {
  return roundCurrency(portfolioValueUsd * (lossPercentValue / 100));
}

export function roundCurrency(value: number): number {
  return Math.round(value * 100) / 100;
}
