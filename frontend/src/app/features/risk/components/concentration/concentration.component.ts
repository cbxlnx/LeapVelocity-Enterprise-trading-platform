import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { ConcentrationView, HoldingExposure, RiskPortfolio } from '../../models/risk-dashboard.model';
import { formatAmount } from '../../utils/risk-calculations';
@Component({ selector: 'app-concentration', standalone: true, templateUrl: './concentration.component.html',
  styleUrl: './concentration.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class ConcentrationComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly view = signal<ConcentrationView>('stock');
  readonly largest = computed<HoldingExposure | null>(() => this.portfolio().valuationReady ? this.portfolio().holdings[0] ?? null : null);
  readonly format = formatAmount;
  selectView(view: ConcentrationView): void { this.view.set(view); }
}
