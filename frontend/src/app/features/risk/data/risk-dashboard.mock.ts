import {
  HoldingRisk,
  RiskDashboardData,
  RiskPortfolio,
  SectorName,
  ShockValues,
  StressScenario,
  WaterfallDelta,
} from '../models/risk-dashboard.model';
import {
  calculateAllocations,
  calculatePortfolioValue,
  calculateSectorExposure,
  reconcileWaterfall,
} from '../utils/risk-calculations';

const baselineShocks: ShockValues = {
  market: -10,
  Technology: 0,
  Financials: 0,
  Energy: 0,
  Healthcare: 0,
};

const distribution = [
  [-12000, 4], [-10500, 7], [-9000, 12], [-7500, 20], [-6000, 35], [-5250, 55],
  [-4500, 85], [-3750, 140], [-3000, 210], [-2250, 300], [-1500, 420], [-750, 540],
  [0, 930], [750, 890], [1500, 830], [2250, 690], [3000, 540], [3750, 420],
  [4500, 300], [5250, 210], [6000, 125], [6750, 75], [7500, 48], [8250, 29],
  [9000, 18], [9750, 10], [10500, 6], [11250, 3], [12000, 2],
].map(([returnUsd, frequency]) => ({ returnUsd, frequency }));

const drawdownSeries: Array<{ date: string; percent: number }> = [
  ['2025-10-13', 0], ['2025-10-27', -0.5], ['2025-11-10', -1.1], ['2025-11-24', -0.7],
  ['2025-12-08', -2.0], ['2025-12-22', -1.6], ['2026-01-05', -3.1], ['2026-01-19', -1.6],
  ['2026-02-02', -0.2], ['2026-02-16', -2.8], ['2026-03-02', -1.8], ['2026-03-16', -4.6],
  ['2026-03-30', -3.5], ['2026-04-13', -5.8], ['2026-04-27', -7.1], ['2026-05-08', -12.4],
  ['2026-05-18', -10.2], ['2026-06-01', -8.2], ['2026-06-15', -6.3], ['2026-06-29', -5.1],
  ['2026-07-13', -5.4], ['2026-07-27', -4.0], ['2026-08-10', -3.1], ['2026-08-24', -2.2],
  ['2026-09-07', -1.4], ['2026-09-21', -0.8], ['2026-10-05', -1.0], ['2026-10-12', -0.3],
].map(([date, percent]) => ({ date: String(date), percent: Number(percent) }));

function buildHoldings(values: number[], contributionShares: number[]): HoldingRisk[] {
  const definitions: Array<[string, string, SectorName, number, number, number]> = [
    ['MSFT', 'Microsoft Corporation', 'Technology', 1.16, 1062, 25],
    ['AAPL', 'Apple Inc.', 'Technology', 1.25, 1114, 28],
    ['JPM', 'JPMorgan Chase & Co.', 'Financials', 1.12, 783, 24],
    ['XOM', 'Exxon Mobil Corporation', 'Energy', 1.02, 781, 26],
    ['JNJ', 'Johnson & Johnson', 'Healthcare', 0.65, 308, 16],
  ];
  const allocations = calculateAllocations(values);
  return definitions.map(([symbol, company, sector, beta, standaloneVarUsd, volatility]) => {
    const index = definitions.findIndex((definition) => definition[0] === symbol);
    return {
      symbol,
      company,
      sector,
      marketValue: values[index],
      allocationPercent: allocations[index],
      beta,
      standaloneVarUsd,
      annualizedVolatilityPercent: volatility,
      volatilityContributionPercent: contributionShares[index],
    };
  });
}

function makeWaterfall(
  title: string,
  period: string,
  startingEquityUsd: number,
  deltas: Array<[string, number]>,
  cashReserveUsd: number,
): RiskPortfolio['historicalWaterfalls'][number] {
  const items: WaterfallDelta[] = deltas.map(([label, deltaUsd]) => ({ label, deltaUsd }));
  return {
    title,
    period,
    startingEquityUsd,
    deltas: items,
    endingEquityUsd: reconcileWaterfall(startingEquityUsd, items),
    cashReserveUsd,
  };
}

function makePortfolio(
  accountId: string,
  accountLabel: string,
  cashReserveUsd: number,
  holdingValues: number[],
  oneDayVar: RiskPortfolio['oneDayVar'],
  drawdown: number,
): RiskPortfolio {
  const holdings = buildHoldings(holdingValues, [26.5, 28, 19.5, 19, 7]);
  const investedEquityUsd = holdings.reduce((total, holding) => total + (holding.marketValue ?? 0), 0);
  const portfolioValueUsd = calculatePortfolioValue(investedEquityUsd, cashReserveUsd);
  const scenarios: StressScenario[] = [
    {
      id: 'financial-crisis-2008',
      title: '2008 Financial Crisis',
      period: '09 Oct 2007 – 09 Mar 2009',
      description: 'Event replay',
      kind: 'historical',
      lossUsd: 65360,
      lossPercent: 16,
      waterfall: makeWaterfall('2008 Financial Crisis', '09 Oct 2007 – 09 Mar 2009', portfolioValueUsd, [
        ['MSFT', -16400], ['AAPL', -17280], ['JPM', -15750], ['XOM', -10150], ['JNJ', -5780],
      ], cashReserveUsd),
    },
    {
      id: 'covid-crash-2020',
      title: 'March 2020 Crash',
      period: '19 Feb – 23 Mar 2020',
      description: 'Event replay',
      kind: 'historical',
      lossUsd: 44935,
      lossPercent: 11,
      waterfall: makeWaterfall('March 2020 Crash', '19 Feb – 23 Mar 2020', portfolioValueUsd, [
        ['MSFT', -10250], ['AAPL', -10752], ['JPM', -11025], ['XOM', -10150], ['JNJ', -2758],
      ], cashReserveUsd),
    },
    {
      id: 'custom-shock',
      title: 'Custom shock',
      period: 'Market -10% · Sector increments 0%',
      description: 'Cash unchanged',
      kind: 'custom',
      lossUsd: 15850,
      lossPercent: 3.88,
    },
  ];
  return {
    accountId,
    accountLabel,
    asOf: '12 Oct 2026 · 14:32 UTC',
    historicalFrom: '13 Oct 2025',
    historicalTo: '12 Oct 2026',
    cashReserveUsd,
    investedEquityUsd,
    portfolioValueUsd,
    oneDayVar,
    expectedShortfallUsd: 5280,
    volatilityPercent: 9.2,
    beta: 0.42,
    maximumDrawdownPercent: drawdown,
    maximumDrawdownDate: '08 May 2026',
    dailyReturnCount: 252,
    monteCarloPathCount: 10000,
    holdings,
    distribution,
    drawdownSeries,
    insights: [
      { label: 'Largest stock exposure', value: 'MSFT · 25.9%', detail: '$41,000 · invested equity' },
      { label: 'Largest sector exposure', value: 'Technology · 50.1%', detail: '$79,400 · MSFT + AAPL' },
      { label: 'Cash reserve', value: `${((cashReserveUsd / portfolioValueUsd) * 100).toFixed(2)}%`, detail: `$${cashReserveUsd.toLocaleString('en-US')} · total account equity` },
      { label: 'Historical data coverage', value: '5 / 5 holdings', detail: '252 returns · 13 Oct 2025 – 12 Oct 2026' },
    ],
    sectorExposure: calculateSectorExposure(holdings),
    scenarios,
    historicalWaterfalls: scenarios.flatMap((scenario) => scenario.waterfall ? [scenario.waterfall] : []),
  };
}

export const RISK_DASHBOARD_SEED: RiskDashboardData = {
  portfolios: [
    makePortfolio('ACC-001', 'All holdings', 250000, [41000, 38400, 31500, 29000, 18600], {
      historicalUsd: 3920,
      parametricUsd: 3890,
      monteCarloUsd: 4210,
    }, -12.4),
    makePortfolio('ACC-002', 'Growth sleeve', 120000, [32000, 29000, 18000, 9000, 7000], {
      historicalUsd: 2300,
      parametricUsd: 2180,
      monteCarloUsd: 2510,
    }, -9.6),
  ],
};

export const BASELINE_SHOCKS = baselineShocks;
