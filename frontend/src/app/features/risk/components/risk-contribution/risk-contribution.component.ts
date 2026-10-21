import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { EChartsOption } from 'echarts';
import { RiskPortfolio } from '../../models/risk-dashboard.model';
import { createContributionOptions } from '../../utils/risk-chart-options';
import { calculateRiskContributionTotal } from '../../utils/risk-calculations';

@Component({
  selector: 'app-risk-contribution',
  standalone: true,
  imports: [NgxEchartsDirective],
  templateUrl: './risk-contribution.component.html',
  styleUrl: './risk-contribution.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskContributionComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly options = computed<EChartsOption>(() => createContributionOptions(this.portfolio()));
  readonly contributionTotal = computed(() => calculateRiskContributionTotal(this.portfolio().holdings.map((holding) => holding.volatilityContributionPercent)));
}
