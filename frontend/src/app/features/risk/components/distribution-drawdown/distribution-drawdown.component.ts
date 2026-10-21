import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { RiskPortfolio } from '../../models/risk-dashboard.model';
import { createDrawdownOptions } from '../../utils/risk-chart-options';
import { formatAmount } from '../../utils/risk-calculations';
@Component({ selector: 'app-distribution-drawdown', standalone: true, imports: [NgxEchartsDirective],
  templateUrl: './distribution-drawdown.component.html', styleUrl: './distribution-drawdown.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class DistributionDrawdownComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly maximum = computed(() => this.portfolio().assessment.metrics.maximumDrawdown);
  readonly drawdownOptions = computed(() => createDrawdownOptions(this.portfolio().drawdownSeries,
    this.maximum().status === 'READY' ? this.maximum().value : null));
  readonly format = formatAmount;
}
