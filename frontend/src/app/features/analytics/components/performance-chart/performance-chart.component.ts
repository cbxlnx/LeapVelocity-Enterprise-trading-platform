import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PerformancePoint } from '../../models/portfolio-performance.model';
import { formatTradingDate } from '../../models/trading-date';

@Component({
  selector: 'app-performance-chart', standalone: true, imports: [DecimalPipe],
  templateUrl: './performance-chart.component.html', styleUrl: './performance-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerformanceChartComponent {
  readonly points = input.required<PerformancePoint[]>();
  readonly formatDate = formatTradingDate;
  readonly chart = computed(() => {
    const points = this.points();
    if (points.length === 0) return null;
    const left = 80, top = 24, width = 840, height = 260;
    const values = points.flatMap((point) => [point.portfolioIndex, point.benchmarkIndex]);
    const low = Math.min(...values), high = Math.max(...values);
    const padding = Math.max((high - low) * 0.1, high * 0.02, 0.01);
    const min = Math.max(0, low - padding), max = high + padding;
    const time = (day: string) => Date.parse(`${day}T00:00:00Z`);
    const first = time(points[0].date);
    const span = time(points[points.length - 1].date) - first;
    const y = (value: number) => top + (max - value) / (max - min) * height;
    const plotted = points.map((point) => ({ ...point,
      x: span === 0 ? left + width / 2 : left + (time(point.date) - first) / span * width,
      portfolioY: y(point.portfolioIndex), benchmarkY: y(point.benchmarkIndex),
    }));
    const path = (key: 'portfolioY' | 'benchmarkY') => plotted.map((point) => `${point.x.toFixed(2)},${point[key].toFixed(2)}`).join(' ');
    return {
      points: plotted, portfolioLine: path('portfolioY'), benchmarkLine: path('benchmarkY'),
      yTicks: Array.from({ length: 5 }, (_, i) => ({ y: top + i * height / 4, value: max - i * (max - min) / 4 })),
      xTicks: [...new Set([0, Math.floor((plotted.length - 1) / 2), plotted.length - 1])].map((index) => plotted[index]),
      left, right: left + width, bottom: top + height, baseline: y(100),
    };
  });
}
