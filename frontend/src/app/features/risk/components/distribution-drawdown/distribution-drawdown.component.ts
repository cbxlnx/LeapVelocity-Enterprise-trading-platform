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
}
