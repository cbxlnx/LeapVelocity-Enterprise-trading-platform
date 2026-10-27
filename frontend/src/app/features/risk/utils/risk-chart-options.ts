import { EChartsOption } from 'echarts';
import { DistributionBin, DrawdownPoint, RiskHorizon, RiskPortfolio, ScenarioWaterfall } from '../models/risk-dashboard.model';
import { getVaRForHorizon } from './risk-calculations';

const axisColor = '#586c7d';
const gridColor = '#e5ebef';

export function createVarChartOptions(portfolio: RiskPortfolio, horizon: RiskHorizon): EChartsOption {
  const values = getVaRForHorizon(portfolio.oneDayVar, horizon);
  return {
    animation: false,
    grid: { left: 76, right: 104, top: 8, bottom: 34, containLabel: true },
    xAxis: { type: 'value', max: Math.max((values.monteCarloUsd ?? 0) * 1.2, 5000), axisLabel: { color: axisColor, fontSize: 9, formatter: (value: number) => `$${(value / 1000).toFixed(1)}k` }, splitLine: { lineStyle: { color: gridColor } } },
    yAxis: { type: 'category', data: ['Historical', 'Parametric', 'Monte Carlo'], axisLabel: { color: '#20364e', fontSize: 11 } },
    tooltip: { trigger: 'axis', className: 'chart-tooltip', axisPointer: { type: 'shadow' }, valueFormatter: (value) => `$${Number(value).toLocaleString('en-US')}` },
    series: [{
      type: 'bar',
      data: [values.historicalUsd, values.parametricUsd, values.monteCarloUsd].map((value, index) => ({ value: value ?? 0, itemStyle: { color: ['#142337', '#7890a4', '#3679b3'][index] } })),
      barWidth: 18,
      showBackground: true,
      backgroundStyle: { color: '#f0f3f5' },
      label: { show: true, position: 'right', color: '#142337', fontFamily: 'IBM Plex Mono', fontSize: 10, formatter: (params) => portfolio.portfolioValueUsd === null ? 'Unavailable' : `$${Number(params.value).toLocaleString('en-US')}  ${((Number(params.value) / portfolio.portfolioValueUsd) * 100).toFixed(2)}%` },
    }],
  };
}

export function createHistogramOptions(bins: DistributionBin[], varUsd: number | null): EChartsOption {
  const threshold = varUsd ?? 0;
  return {
    animation: false,
    grid: { left: 42, right: 12, top: 24, bottom: 34, containLabel: true },
    xAxis: { type: 'category', data: bins.map((bin) => bin.returnUsd), axisLabel: { color: axisColor, fontSize: 9, formatter: (value: string) => Number(value) % 3000 === 0 ? `${Number(value) >= 0 ? '+' : ''}${Number(value) / 1000}k` : '' }, axisLine: { lineStyle: { color: '#cbd5dc' } } },
    yAxis: { type: 'value', axisLabel: { color: axisColor, fontSize: 9 }, splitLine: { lineStyle: { color: gridColor } } },
    tooltip: { trigger: 'axis', className: 'chart-tooltip', valueFormatter: (value) => `${Number(value).toLocaleString('en-US')} paths` },
    series: [{
      type: 'bar',
      barCategoryGap: '8%',
      data: bins.map((bin) => ({ value: bin.frequency, itemStyle: { color: bin.returnUsd <= -threshold ? '#bb3842' : '#3679b3' } })),
      markLine: { symbol: 'none', lineStyle: { type: 'dashed', color: '#bb3842', width: 1.5 }, label: { color: '#bb3842', formatter: varUsd === null ? 'Monte Carlo VaR unavailable' : `95% Monte Carlo VaR: -$${varUsd.toLocaleString('en-US')}` }, data: [{ xAxis: bins.find((bin) => bin.returnUsd >= -threshold)?.returnUsd ?? -threshold }] },
      markArea: { silent: true, itemStyle: { color: 'rgba(187, 56, 66, 0.05)' }, data: [[{ xAxis: bins[0]?.returnUsd }, { xAxis: -threshold }]] },
    }],
  };
}

export function createDrawdownOptions(points: DrawdownPoint[], maximumDrawdownPercent: number | null, maximumDrawdownDate: string | null): EChartsOption {
  const minimumDrawdown = Math.min(...points.map((point) => point.percent), 0);
  const drawdownPadding = Math.max(1, Math.abs(minimumDrawdown) * 0.1);
  const tickInterval = Math.abs(minimumDrawdown) >= 20 ? 10 : 5;
  const lowerBound = Math.floor((minimumDrawdown - drawdownPadding) / tickInterval) * tickInterval;
  return {
    animation: false,
    grid: { left: 42, right: 18, top: 18, bottom: 34, containLabel: true },
    xAxis: { type: 'category', data: points.map((point) => point.date), axisLabel: { color: axisColor, fontSize: 9, hideOverlap: true, formatter: (value: string) => value.slice(5) }, axisLine: { lineStyle: { color: '#cbd5dc' } } },
    yAxis: { type: 'value', max: 0, min: lowerBound, interval: tickInterval, axisLabel: { color: axisColor, fontSize: 9, formatter: (value: number) => `${value}%` }, splitLine: { lineStyle: { color: gridColor } } },
    tooltip: { trigger: 'axis', className: 'chart-tooltip', valueFormatter: (value) => `${Number(value).toFixed(2)}%` },
    series: [{
      type: 'line',
      data: points.map((point) => point.percent),
      smooth: false,
      showSymbol: false,
      lineStyle: { color: '#3679b3', width: 1.5 },
      itemStyle: { color: '#bb3842' },
      areaStyle: { color: 'rgba(54, 121, 179, 0.18)' },
      markPoint: { symbol: 'circle', symbolSize: 7, itemStyle: { color: '#bb3842' }, label: { show: true, position: 'right', color: '#bb3842', formatter: maximumDrawdownPercent === null ? 'Unavailable' : `${maximumDrawdownPercent.toFixed(2)}%` }, data: maximumDrawdownDate === null || maximumDrawdownPercent === null ? [] : [{ name: 'Maximum drawdown', coord: [maximumDrawdownDate, maximumDrawdownPercent] as [string, number] }] },
    }],
  };
}

export function createContributionOptions(portfolio: RiskPortfolio): EChartsOption {
  return {
    animation: false,
    grid: { left: 54, right: 52, top: 8, bottom: 28, containLabel: true },
    legend: { top: 0, right: 0, textStyle: { color: axisColor, fontSize: 10 } },
    xAxis: { type: 'value', max: 35, axisLabel: { color: axisColor, fontSize: 9, formatter: (value: number) => `${value}%` }, splitLine: { lineStyle: { color: gridColor } } },
    yAxis: { type: 'category', data: portfolio.holdings.map((holding) => holding.symbol), axisLabel: { color: '#20364e', fontSize: 10 } },
    tooltip: { trigger: 'axis', className: 'chart-tooltip', axisPointer: { type: 'shadow' }, valueFormatter: (value) => `${Number(value).toFixed(1)}%` },
    series: [
      { name: 'Allocation · % of invested equity', type: 'bar', data: portfolio.holdings.map((holding) => holding.allocationPercent ?? 0), barWidth: 8, itemStyle: { color: '#aebdca' } },
      { name: 'Share of total portfolio volatility', type: 'bar', data: portfolio.holdings.map((holding) => holding.volatilityContributionPercent ?? 0), barWidth: 8, itemStyle: { color: '#3679b3' } },
    ],
  };
}

export function createWaterfallOptions(waterfall: ScenarioWaterfall): EChartsOption {
  const labels = ['Start', ...waterfall.deltas.map((delta) => delta.label), 'End'];
  const bases: number[] = [];
  const values: number[] = [];
  const colors: string[] = [];
  let current = waterfall.startingEquityUsd;
  for (const delta of waterfall.deltas) {
    const next = current + delta.deltaUsd;
    bases.push(Math.min(current, next));
    values.push(Math.abs(delta.deltaUsd));
    colors.push(delta.deltaUsd < 0 ? '#bb3842' : '#16875d');
    current = next;
  }
  const base = [0, ...bases, 0];
  const visibleValues = [waterfall.startingEquityUsd, ...values, waterfall.endingEquityUsd];
  return {
    animation: false,
    grid: { left: 42, right: 12, top: 22, bottom: 42, containLabel: true },
    xAxis: { type: 'category', data: labels, axisLabel: { color: axisColor, fontSize: 9, rotate: labels.length > 4 ? 24 : 0, hideOverlap: true } },
    yAxis: { type: 'value', axisLabel: { color: axisColor, fontSize: 9, formatter: (value: number) => `${Math.round(value / 1000)}k` }, splitLine: { lineStyle: { color: gridColor } } },
    tooltip: { trigger: 'axis', className: 'chart-tooltip', valueFormatter: (value) => `$${Number(value).toLocaleString('en-US')}` },
    series: [
      { type: 'bar', stack: 'total', itemStyle: { color: 'transparent' }, data: base },
      { type: 'bar', stack: 'total', data: visibleValues.map((value, index) => ({ value, itemStyle: { color: index === 0 ? '#142337' : index === visibleValues.length - 1 ? '#3679b3' : colors[index - 1] } })) },
    ],
  };
}
