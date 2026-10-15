import { DecimalPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { PerformancePeriod, PortfolioPerformance } from '../../models/portfolio-performance.model';
import { formatTradingDate } from '../../models/trading-date';
import { PerformanceChartComponent } from '../performance-chart/performance-chart.component';

@Component({
  selector: 'app-performance-panel', standalone: true,
  imports: [DecimalPipe, PercentPipe, PerformanceChartComponent],
  templateUrl: './performance-panel.component.html', styleUrl: './performance-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerformancePanelComponent {
  readonly performance = input<PortfolioPerformance | null>(null);
  readonly period = input<PerformancePeriod>('1Y');
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly periodChange = output<PerformancePeriod>();
  readonly refresh = output<void>();
  readonly periods: PerformancePeriod[] = ['1M', '3M', '6M', '1Y', '2Y'];
  readonly formatDate = formatTradingDate;

  changePeriod(event: Event): void {
    this.periodChange.emit((event.target as HTMLSelectElement).value as PerformancePeriod);
  }
}
