import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { EChartsOption } from 'echarts';
import { NgxEchartsDirective } from 'ngx-echarts';
import { PerformancePoint } from '../../models/portfolio-performance.model';

@Component({
  selector: 'app-performance-chart', standalone: true, imports: [NgxEchartsDirective],
  templateUrl: './performance-chart.component.html', styleUrl: './performance-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerformanceChartComponent {
  readonly points = input.required<PerformancePoint[]>();
  readonly initOpts = { renderer: 'svg' as const };
  readonly chartOptions = computed<EChartsOption>(() => {
    const points = this.points();
    const labels = points.map(point => point.date);
    return {
      animation: false,
      grid: { left: 54, right: 18, top: 8, bottom: 34 },
      tooltip: { trigger: 'axis', valueFormatter: value => `${Number(Array.isArray(value) ? value[0] : value).toFixed(2)}` },
      xAxis: { type: 'category', data: labels, boundaryGap: false, axisLine: { lineStyle: { color: '#c8d2dc' } }, axisLabel: { color: '#586c7d', fontSize: 11, hideOverlap: true, formatter: (value: string) => this.formatDate(value) } },
      yAxis: { type: 'value', scale: true, splitNumber: 4, axisLabel: { color: '#586c7d', fontSize: 11, formatter: (value: number) => value.toFixed(0) }, splitLine: { lineStyle: { color: '#e8ecef' } } },
      series: [
        { name: 'Portfolio', type: 'line', data: points.map(point => point.portfolioIndex), showSymbol: false, color: '#173b63', lineStyle: { color: '#173b63', width: 2.5 }, itemStyle: { color: '#173b63' }, areaStyle: { color: 'rgba(23, 59, 99, 0.10)' } },
        { name: 'SPY', type: 'line', data: points.map(point => point.benchmarkIndex), showSymbol: false, color: '#c05819', lineStyle: { color: '#c05819', width: 2, type: 'dashed' }, itemStyle: { color: '#c05819' } },
        { name: 'Start = 100', type: 'line', data: points.map(() => 100), showSymbol: false, silent: true, color: '#aebbc8', lineStyle: { color: '#aebbc8', width: 1, type: 'dashed' }, itemStyle: { color: '#aebbc8' } },
      ],
    };
  });

  private formatDate(date: string): string {
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' }).format(new Date(`${date}T00:00:00Z`));
  }
}
