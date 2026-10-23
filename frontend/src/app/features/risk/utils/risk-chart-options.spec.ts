import { describe, expect, it } from 'vitest';
import { RISK_DASHBOARD_SEED } from '../data/risk-dashboard.mock';
import { createContributionOptions, createDrawdownOptions, createHistogramOptions, createVarChartOptions, createWaterfallOptions } from './risk-chart-options';

describe('risk chart option factories', () => {
  const portfolio = RISK_DASHBOARD_SEED.portfolios[0];

  it('creates a three-model VaR chart for the selected horizon', () => {
    const options = createVarChartOptions(portfolio, 10);
    expect(options.yAxis).toMatchObject({ data: ['Historical', 'Parametric', 'Monte Carlo'] });
    expect(options.grid).toMatchObject({ left: 76, containLabel: true });
    expect(options.series).toHaveLength(1);
  });

  it('marks the histogram tail at the Monte Carlo VaR threshold', () => {
    const options = createHistogramOptions(portfolio.distribution, portfolio.oneDayVar.monteCarloUsd);
    const series = Array.isArray(options.series) ? options.series[0] : options.series;
    expect(series).toHaveProperty('markLine');
    expect(series).toHaveProperty('markArea');
  });

  it('adds a max drawdown point and two contribution series', () => {
    const drawdown = createDrawdownOptions(portfolio.drawdownSeries, portfolio.maximumDrawdownPercent, portfolio.maximumDrawdownDate);
    const drawdownSeries = Array.isArray(drawdown.series) ? drawdown.series[0] : drawdown.series;
    expect(drawdown.yAxis).toMatchObject({ max: 0, min: expect.closeTo(-13.64, 2) });
    expect(drawdownSeries).toHaveProperty('markPoint');
    expect((drawdownSeries as { markPoint: { data: Array<{ coord: [string, number] }> } }).markPoint.data[0].coord[1]).toBe(-12.4);
    const contribution = createContributionOptions(portfolio);
    expect(contribution.series).toHaveLength(2);
  });

  it('aligns signed waterfall steps to the preceding cumulative value', () => {
    const waterfall = {
      title: 'Mixed moves',
      period: 'Test',
      startingEquityUsd: 100,
      deltas: [{ label: 'Loss', deltaUsd: -20 }, { label: 'Gain', deltaUsd: 10 }, { label: 'Loss 2', deltaUsd: -5 }],
      endingEquityUsd: 85,
      cashReserveUsd: 0,
    };
    const options = createWaterfallOptions(waterfall);
    const series = options.series as Array<{ data: Array<number | { value: number; itemStyle?: { color: string } }> }>;
    const values = (data: Array<number | { value: number }>) => data.map((item) => typeof item === 'number' ? item : item.value);

    expect(values(series[0].data)).toEqual([0, 80, 80, 85, 0]);
    expect(values(series[1].data)).toEqual([100, 20, 10, 5, 85]);
    expect(series[1].data.slice(1, 4).map((item) => typeof item === 'number' ? undefined : item.itemStyle?.color)).toEqual(['#bb3842', '#16875d', '#bb3842']);
  });
});
