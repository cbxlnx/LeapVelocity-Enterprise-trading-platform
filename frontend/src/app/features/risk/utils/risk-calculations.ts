import {
  HoldingRisk,
  RiskHorizon,
  SectorExposure,
  ScenarioWaterfall,
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

export function scaleVaR(oneDayLossUsd: number | null, horizon: RiskHorizon): number | null {
  return oneDayLossUsd === null ? null : roundCurrency(oneDayLossUsd * Math.sqrt(horizon));
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

export function calculateRiskContributionTotal(contributions: Array<number | null>): number {
  return contributions.reduce<number>((total, contribution) => total + (contribution ?? 0), 0);
}

export function calculateSectorExposure(holdings: HoldingRisk[]): SectorExposure[] {
  const grouped = new Map<string, { marketValue: number; symbols: string[] }>();
  for (const holding of holdings) {
    if (holding.marketValue === null || holding.sector === null) continue;
    const current = grouped.get(holding.sector) ?? { marketValue: 0, symbols: [] };
    current.marketValue += holding.marketValue;
    current.symbols.push(holding.symbol);
    grouped.set(holding.sector, current);
  }
  const investedEquityUsd = holdings.reduce((total, holding) => total + (holding.marketValue ?? 0), 0);
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
  cashReserveUsd = portfolioValueUsd - holdings.reduce((total, holding) => total + (holding.marketValue ?? 0), 0),
): ShockResult {
  const deltas = holdings.map((holding) => {
    const sectorShock = holding.sector && holding.sector in shocks ? shocks[holding.sector as keyof ShockValues] : 0;
    const combinedShockPercent = shocks.market + (typeof sectorShock === 'number' ? sectorShock : 0);
    return {
      label: holding.symbol,
      deltaUsd: roundCurrency((holding.marketValue ?? 0) * (combinedShockPercent / 100)),
    };
  });
  const totalImpactUsd = deltas.reduce((total, delta) => total + delta.deltaUsd, 0);
  const roundedLoss = roundCurrency(-totalImpactUsd);
  const waterfall: ScenarioWaterfall = {
    title: 'Custom scenario',
    period: 'Current positions',
    startingEquityUsd: roundCurrency(portfolioValueUsd),
    deltas,
    endingEquityUsd: roundCurrency(portfolioValueUsd + totalImpactUsd),
    cashReserveUsd: roundCurrency(cashReserveUsd),
  };
  return {
    lossUsd: roundedLoss,
    impactPercent: lossPercent(roundedLoss, portfolioValueUsd),
    postShockEquityUsd: waterfall.endingEquityUsd,
    waterfall,
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
