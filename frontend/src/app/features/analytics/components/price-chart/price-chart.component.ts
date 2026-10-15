import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { HistoricalPricePoint } from '../../models/historical-prices.model';
import { formatTradingDate } from '../../models/trading-date';

@Component({
  selector: 'app-price-chart',
  standalone: true,
  imports: [CurrencyPipe, DecimalPipe],
  templateUrl: './price-chart.component.html',
  styleUrl: './price-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceChartComponent {
  readonly prices = input.required<HistoricalPricePoint[]>();
  readonly currency = input('USD');
  readonly symbol = input.required<string>();
  readonly formatDate = formatTradingDate;

  readonly chart = computed(() => {
    const prices = this.prices();
    if (prices.length === 0) return null;
    const left = 80, top = 24, width = 840, height = 260;
    const values = prices.map((point) => point.close);
    const low = Math.min(...values), high = Math.max(...values);
    const padding = Math.max((high - low) * 0.1, high * 0.02, 0.01);
    const min = Math.max(0, low - padding), max = high + padding;
    const time = (day: string) => Date.parse(`${day}T00:00:00Z`);
    const firstTime = time(prices[0].date);
    const span = time(prices[prices.length - 1].date) - firstTime;
    const points = prices.map((point) => ({
      ...point,
      x: span === 0 ? left + width / 2 : left + (time(point.date) - firstTime) / span * width,
      y: top + (max - point.close) / (max - min) * height,
    }));
    const tickIndices = [...new Set([0, Math.floor((points.length - 1) / 2), points.length - 1])];
    return {
      points,
      line: points.map((point) => `${point.x.toFixed(2)},${point.y.toFixed(2)}`).join(' '),
      yTicks: Array.from({ length: 5 }, (_, i) => ({ y: top + i * height / 4, value: max - i * (max - min) / 4 })),
      xTicks: tickIndices.map((index) => points[index]),
      left, right: left + width, bottom: top + height,
    };
  });
}
