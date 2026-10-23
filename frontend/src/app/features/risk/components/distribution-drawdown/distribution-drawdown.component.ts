import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { EChartsOption } from 'echarts';
import { RiskPortfolio } from '../../models/risk-dashboard.model';
import { createDrawdownOptions, createHistogramOptions } from '../../utils/risk-chart-options';

@Component({
  selector: 'app-distribution-drawdown',
  standalone: true,
  imports: [NgxEchartsDirective],
  templateUrl: './distribution-drawdown.component.html',
  styleUrl: './distribution-drawdown.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DistributionDrawdownComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly histogramOptions = computed<EChartsOption>(() => createHistogramOptions(this.portfolio().distribution, this.portfolio().oneDayVar.monteCarloUsd));
  readonly drawdownOptions = computed<EChartsOption>(() => createDrawdownOptions(this.portfolio().drawdownSeries, this.portfolio().maximumDrawdownPercent, this.portfolio().maximumDrawdownDate));

  metric(value: number | null, digits = 2): string {
    return value === null ? 'Unavailable' : value.toFixed(digits);
  }

  currency(value: number | null): string {
    return value === null ? 'Unavailable' : value.toLocaleString('en-US');
  }

  ratio(value: number | null, total: number | null): string {
    return value === null || total === null || total === 0 ? 'Unavailable' : (value / total * 100).toFixed(2);
  }
}
