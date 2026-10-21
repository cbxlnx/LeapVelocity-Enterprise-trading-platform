import { describe, expect, it } from 'vitest';
import { RISK_DASHBOARD_SEED } from '../data/risk-dashboard.mock';
import { createContributionOptions, createDrawdownOptions, createHistogramOptions, createVarChartOptions } from './risk-chart-options';

describe('risk chart option factories', () => {
  const portfolio = RISK_DASHBOARD_SEED.portfolios[0];

  it('creates a three-model VaR chart for the selected horizon', () => {
    const options = createVarChartOptions(portfolio, 10);
    expect(options.yAxis).toMatchObject({ data: ['Historical', 'Parametric', 'Monte Carlo'] });
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
    expect(drawdownSeries).toHaveProperty('markPoint');
    const contribution = createContributionOptions(portfolio);
    expect(contribution.series).toHaveLength(2);
  });
});
