import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { EChartsOption } from 'echarts';
import { NgxEchartsDirective } from 'ngx-echarts';
import { HistoricalPrices } from '../../models/historical-prices.model';

@Component({
  selector: 'app-comparison-chart',
  standalone: true,
  imports: [NgxEchartsDirective],
  template: '<div echarts [options]="options()" [initOpts]="initOpts" class="comparison-chart"></div>',
  styleUrl: './comparison-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonChartComponent {
  readonly histories = input.required<HistoricalPrices[]>();
  readonly initOpts = { renderer: 'svg' as const };
  private readonly seriesColors = ['#075985', '#c2410c', '#047857', '#7e22ce', '#be123c', '#0f766e', '#a16207', '#4338ca'];

  readonly options = computed<EChartsOption>(() => {
    const histories = this.histories();
    const labels = histories[0]?.prices.map(point => point.date) ?? [];
    return {
      animation: false,
      grid: { left: 46, right: 18, top: 18, bottom: 30 },
      tooltip: { trigger: 'axis', className: 'chart-tooltip', valueFormatter: (value) => `${Number(Array.isArray(value) ? value[0] : value).toFixed(2)}` },
      legend: { top: 0, left: 0, textStyle: { color: '#586c7d', fontSize: 10 } },
      xAxis: { type: 'category', data: labels, axisLabel: { color: '#718296', fontSize: 9, hideOverlap: true }, axisLine: { lineStyle: { color: '#c8d2dc' } } },
      yAxis: { type: 'value', axisLabel: { color: '#718296', fontSize: 9, formatter: (value: number) => `${value.toFixed(0)}` }, splitLine: { lineStyle: { color: '#e8ecef' } } },
      series: histories.map((history, index) => {
        const color = this.seriesColors[index % this.seriesColors.length];
        return {
        name: history.symbol,
        type: 'line',
        showSymbol: false,
        data: history.prices.map(point => point.close / history.prices[0].close * 100),
        color,
        lineStyle: { width: 2.5, color },
        itemStyle: { color },
        };
      }),
    };
  });
}
