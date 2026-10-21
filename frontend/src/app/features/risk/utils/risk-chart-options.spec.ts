import { describe, expect, it } from 'vitest';
import { riskFixture, shockFixture, TEST_INSTRUMENTS } from '../testing/risk-dashboard.fixture';
import { createContributionOptions, createDrawdownOptions, createDistributionOptions, createVarChartOptions, createWaterfallOptions } from './risk-chart-options';
import { toShockResult, toRiskPortfolio } from './risk-calculations';

describe('real account chart options', () => {
  it('plots backend horizon amounts without square-root scaling or missing-value zeros', () => {
    const response = riskFixture();
    response.parameters.holdingPeriodDays = 10;
    response.valueAtRisk[1].status = 'UNAVAILABLE';
    response.valueAtRisk[1].lossAmount = null;
    response.valueAtRisk[1].lossPercent = null;
    response.valueAtRisk[1].reasons = ['INSUFFICIENT_HISTORY'];
    expect(createVarChartOptions(response).series).toMatchObject([{ data: [50, null, 55] }]);
  });

  it('uses observed dates and an axis that does not clip deep drawdowns', () => {
    const points = [{ date: '2026-01-02', percent: 0 }, { date: '2026-01-05', percent: -80 }];
    const options = createDrawdownOptions(points, 80);
    expect(options.xAxis).toMatchObject({ data: points.map(p => p.date) });
    expect(options.yAxis).toMatchObject({ min: -88, max: 0 });
    expect(options.series).toMatchObject([{ markPoint: { data: [{ coord: ['2026-01-05', -80] }] } }]);
  });

  it('shows allocation without inventing volatility contribution', () => {
    const options = createContributionOptions(toRiskPortfolio(riskFixture(), TEST_INSTRUMENTS));
    expect(options.series).toHaveLength(1);
    expect(options.series).toMatchObject([{ data: [60, 40] }]);
  });

  it('maps backend holding deltas to their actual waterfall baselines', () => {
    const loss = toShockResult(shockFixture())!;
    expect(createWaterfallOptions(loss.waterfall).series).toMatchObject([{ data: [0, 1040, 1000, 0] }, {}]);
  });

  it('uses the supplied histogram and marks backend VaR and ES on the loss axis', () => {
    const options = createDistributionOptions(riskFixture().monteCarlo);
    expect(options.series).toMatchObject([{ data: [[-50, 50000], [50, 50000]],
      markLine: { data: [{ name: 'VaR', xAxis: 55 }, { name: 'ES', xAxis: 70 }] } }]);
    expect(createDistributionOptions(undefined)).toEqual({ series: [] });
  });

  it('keeps fractional-dollar waterfall baselines at the scenario amounts', () => {
    const options = createWaterfallOptions({
      title: 'Custom uniform price shock', period: 'Test valuation', startingEquityUsd: .4,
      endingEquityUsd: .43, cashReserveUsd: .1,
      deltas: [{ label: 'AAA', deltaUsd: .01 }, { label: 'BBB', deltaUsd: .02 }],
    });
    expect(options.series).toMatchObject([{ data: [0, .4, .41, 0] }, { data: [.4, { value: .01 }, { value: .02 }, .43] }]);
  });
});
