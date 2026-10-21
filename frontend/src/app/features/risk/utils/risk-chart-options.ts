import { EChartsOption } from 'echarts';
import { PortfolioRisk, MonteCarloDistribution } from '../../analytics/models/portfolio-risk.model';
import { DrawdownPoint, ScenarioWaterfall } from '../models/risk-dashboard.model';
import { formatAmount } from './risk-calculations';
import { RiskDecimal } from './risk-decimal';

const axisColor = '#586c7d';
const gridColor = '#e5ebef';

/** Histogram counts and markers come from the same backend simulation. */
export function createDistributionOptions(distribution: MonteCarloDistribution | undefined): EChartsOption {
  if (!distribution || distribution.status !== 'READY') return { series: [] };
  const bins = distribution.bins;
  const low = Math.min(...bins.map(bin => bin.lowerBound), distribution.varAmount ?? 0);
  const high = Math.max(...bins.map(bin => bin.upperBound), distribution.expectedShortfallAmount ?? 0);
  const padding = Math.max((high - low) * .06, .01);
  return {
    animation: false,
    grid: { left: 55, right: 24, top: 35, bottom: 48 },
    xAxis: { type: 'value', name: 'USD loss (negative = gain)', nameLocation: 'middle', nameGap: 32,
      min: low - padding, max: high + padding,
      axisLabel: { color: axisColor, fontSize: 9, formatter: (value: number) => formatAmount(value, '$') } },
    yAxis: { type: 'value', min: 0, name: 'Outcomes', splitLine: { lineStyle: { color: gridColor } } },
    tooltip: { trigger: 'item', formatter: params => {
      const entry = Array.isArray(params) ? params[0] : params;
      const bin = bins[entry.dataIndex];
      return bin ? `${formatAmount(bin.lowerBound, '$')} to ${formatAmount(bin.upperBound, '$')}: ${bin.count.toLocaleString()} outcomes` : '';
    } },
    series: [{ type: 'bar', barWidth: bins.length === 1 ? 16 : '90%',
      data: bins.map(bin => [bin.lowerBound + (bin.upperBound - bin.lowerBound) / 2, bin.count]),
      itemStyle: { color: '#3679b3' },
      markLine: { symbol: 'none', label: { formatter: '{b}', fontSize: 10 }, data: [
        { name: 'VaR', xAxis: distribution.varAmount!, lineStyle: { color: '#bb3842' } },
        { name: 'ES', xAxis: distribution.expectedShortfallAmount!, lineStyle: { color: '#a86413', type: 'dashed' } },
      ] },
    }],
  };
}

export function createVarChartOptions(assessment: PortfolioRisk): EChartsOption {
  const methods = ['HISTORICAL', 'PARAMETRIC', 'MONTE_CARLO'];
  const values = methods.map(method => assessment.valueAtRisk.find(value => value.method === method));
  const amounts = values.map(value => value?.status === 'READY' ? value.lossAmount : null);
  return {
    animation: false,
    grid: { left: 110, right: 104, top: 8, bottom: 34 },
    xAxis: { type: 'value', max: Math.max(...amounts.filter((value): value is number => value != null), 1) * 1.2,
      axisLabel: { color: axisColor, fontSize: 9, formatter: (value: number) => formatAmount(value, '$') }, splitLine: { lineStyle: { color: gridColor } } },
    yAxis: { type: 'category', data: ['Historical', 'Parametric', 'Monte Carlo'], axisLabel: { color: '#20364e', fontSize: 11 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: value => value == null || value === '-' ? 'Unavailable' : formatAmount(Number(value), '$') },
    series: [{ type: 'bar', data: amounts, barWidth: 18, showBackground: true, backgroundStyle: { color: '#f0f3f5' },
      itemStyle: { color: '#3679b3' }, label: { show: true, position: 'right', color: '#142337', fontSize: 10,
        formatter: params => amounts[params.dataIndex] == null ? 'Unavailable'
          : `${formatAmount(amounts[params.dataIndex], '$')} · ${formatAmount(values[params.dataIndex]?.lossPercent, '', '%')}` },
    }],
  };
}

export function createDrawdownOptions(points: DrawdownPoint[], maximumLoss: number | null): EChartsOption {
  const trough = points.reduce<DrawdownPoint | null>((worst, point) => !worst || point.percent < worst.percent ? point : worst, null);
  return {
    animation: false,
    grid: { left: 48, right: 18, top: 18, bottom: 34 },
    xAxis: { type: 'category', data: points.map(point => point.date), axisLabel: { color: axisColor, fontSize: 9, hideOverlap: true }, axisLine: { lineStyle: { color: '#cbd5dc' } } },
    yAxis: { type: 'value', max: 0, min: Math.min(...points.map(point => point.percent), -1) * 1.1,
      axisLabel: { color: axisColor, fontSize: 9, formatter: (value: number) => `${value.toFixed(1)}%` }, splitLine: { lineStyle: { color: gridColor } } },
    tooltip: { trigger: 'axis', valueFormatter: value => formatAmount(Number(value), '', '%') },
    series: [{ type: 'line', data: points.map(point => point.percent), smooth: false, showSymbol: false,
      lineStyle: { color: '#3679b3', width: 1.5 }, areaStyle: { color: 'rgba(54, 121, 179, 0.18)' },
      ...(trough && maximumLoss != null ? { markPoint: { symbol: 'circle', symbolSize: 7,
        label: { show: true, position: 'right' as const, formatter: formatAmount(maximumLoss, maximumLoss ? '−' : '', '%') },
        data: [{ name: 'Maximum drawdown', coord: [trough.date, trough.percent] }] } } : {}),
    }],
  };
}

export function createWaterfallOptions(waterfall: ScenarioWaterfall): EChartsOption {
  let current = new RiskDecimal(waterfall.startingEquityUsd);
  const bases = waterfall.deltas.map(delta => {
    const previous = current;
    current = current.plus(delta.deltaUsd);
    return RiskDecimal.min(previous, current).toNumber();
  });
  return {
    animation: false,
    grid: { left: 64, right: 12, top: 22, bottom: 30 },
    xAxis: { type: 'category', data: ['Start', ...waterfall.deltas.map(delta => delta.label), 'End'], axisLabel: { color: axisColor, fontSize: 9 } },
    yAxis: { type: 'value', axisLabel: { color: axisColor, fontSize: 9, formatter: (value: number) => formatAmount(value, '$') } },
    tooltip: { trigger: 'axis', formatter: params => {
      const entries = Array.isArray(params) ? params : [params];
      const index = entries[0]?.dataIndex ?? 0;
      const label = index === 0 ? 'Starting equity' : index === waterfall.deltas.length + 1 ? 'Ending equity' : waterfall.deltas[index - 1].label;
      const value = index === 0 ? waterfall.startingEquityUsd : index === waterfall.deltas.length + 1 ? waterfall.endingEquityUsd : waterfall.deltas[index - 1].deltaUsd;
      return `${label}: ${formatAmount(value, '$')}`;
    } },
    series: [
      { name: 'Offset', type: 'bar', stack: 'total', silent: true, itemStyle: { color: 'transparent' }, data: [0, ...bases, 0] },
      { name: 'Equity / holding price-change P&L', type: 'bar', stack: 'total', data: [waterfall.startingEquityUsd,
        ...waterfall.deltas.map(delta => ({ value: Math.abs(delta.deltaUsd), itemStyle: { color: delta.deltaUsd < 0 ? '#bb3842' : '#3679b3' } })), waterfall.endingEquityUsd] },
    ],
  };
}
