import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { HoldingExposure, RiskPortfolio } from '../../models/risk-dashboard.model';
import { formatAmount } from '../../utils/risk-calculations';
@Component({ selector: 'app-concentration', standalone: true, templateUrl: './concentration.component.html',
  styleUrl: './concentration.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class ConcentrationComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly largest = computed<HoldingExposure | null>(() => this.portfolio().valuationReady ? this.portfolio().holdings[0] ?? null : null);
  readonly format = formatAmount;
}
