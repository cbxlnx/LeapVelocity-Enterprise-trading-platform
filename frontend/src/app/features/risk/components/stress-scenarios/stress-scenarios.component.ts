import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ShockResult } from '../../models/risk-dashboard.model';
import { formatAmount } from '../../utils/risk-calculations';
@Component({ selector: 'app-stress-scenarios', standalone: true, templateUrl: './stress-scenarios.component.html',
  styleUrl: './stress-scenarios.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class StressScenariosComponent {
  readonly customResult = input<ShockResult | null>(null);
  readonly format = formatAmount;
}
