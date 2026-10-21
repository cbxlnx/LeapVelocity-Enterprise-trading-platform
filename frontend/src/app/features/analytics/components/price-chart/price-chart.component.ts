import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { EChartsOption } from 'echarts';
import { HistoricalPricePoint } from '../../models/historical-prices.model';

@Component({
  selector: 'app-price-chart',
  standalone: true,
  imports: [NgxEchartsDirective],
  templateUrl: './price-chart.component.html',
  styleUrl: './price-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceChartComponent {
  readonly prices = input.required<HistoricalPricePoint[]>();
  readonly currency = input('USD');
  readonly symbol = input.required<string>();
  readonly chartOptions = computed<EChartsOption>(() => {
    const prices = this.prices();
    const labels = prices.map(point => point.date);
    return {
      animation: false,
      grid: [{ left: 56, right: 16, top: 12, height: '58%' }, { left: 56, right: 16, top: '75%', height: '17%' }],
      tooltip: { trigger: 'axis', valueFormatter: (value) => {
        const amount = Array.isArray(value) ? Number(value[0]) : Number(value);
        return Number.isFinite(amount) ? new Intl.NumberFormat('en-US', { style: 'currency', currency: this.currency(), minimumFractionDigits: 2 }).format(amount) : '';
      } },
      xAxis: [
        { type: 'category', data: labels, boundaryGap: false, axisLine: { lineStyle: { color: '#c8d2dc' } }, axisLabel: { color: '#586c7d', hideOverlap: true, formatter: (value: string) => this.formatDate(value) }, gridIndex: 0 },
        { type: 'category', data: labels, boundaryGap: true, axisLine: { lineStyle: { color: '#c8d2dc' } }, axisLabel: { color: '#586c7d', hideOverlap: true, formatter: (value: string) => this.formatDate(value) }, gridIndex: 1 },
      ],
      yAxis: [
        { type: 'value', scale: true, splitNumber: 4, axisLabel: { color: '#586c7d', formatter: (value: number) => value.toFixed(0) }, splitLine: { lineStyle: { color: '#e8ecef' } }, gridIndex: 0 },
        { type: 'value', axisLabel: { color: '#586c7d', formatter: (value: number) => this.formatVolume(value) }, splitLine: { show: false }, gridIndex: 1 },
      ],
      series: [
        { name: this.symbol(), type: 'line', data: prices.map(point => point.close), xAxisIndex: 0, yAxisIndex: 0, showSymbol: false, smooth: false, lineStyle: { color: '#263a52', width: 1.5 }, areaStyle: { color: 'rgba(38, 58, 82, 0.08)' } },
        { name: 'Volume', type: 'bar', data: prices.map(point => point.volume), xAxisIndex: 1, yAxisIndex: 1, barMaxWidth: 26, itemStyle: { color: '#aab8c5' } },
      ],
    };
  });

  private formatDate(date: string): string {
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' }).format(new Date(`${date}T00:00:00Z`));
  }

  private formatVolume(value: number): string {
    return value >= 1_000_000 ? `${(value / 1_000_000).toFixed(0)}M` : `${(value / 1_000).toFixed(0)}K`;
  }
}
