import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { RiskPortfolio } from '../../models/risk-dashboard.model';
import { createContributionOptions } from '../../utils/risk-chart-options';
@Component({ selector: 'app-risk-contribution', standalone: true, imports: [NgxEchartsDirective],
  templateUrl: './risk-contribution.component.html', styleUrl: './risk-contribution.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class RiskContributionComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly options = computed(() => createContributionOptions(this.portfolio()));
}
