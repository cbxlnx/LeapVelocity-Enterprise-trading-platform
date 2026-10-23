import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RiskPortfolio } from '../../models/risk-dashboard.model';

@Component({
  selector: 'app-stress-scenarios',
  standalone: true,
  templateUrl: './stress-scenarios.component.html',
  styleUrl: './stress-scenarios.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StressScenariosComponent {
  readonly math = Math;
  readonly portfolio = input.required<RiskPortfolio>();
  readonly viewBreakdown = output<string>();

  currency(value: number | null): string {
    return value === null ? 'Unavailable' : value.toLocaleString('en-US');
  }

  metric(value: number | null): string {
    return value === null ? 'Unavailable' : value.toFixed(2);
  }
}
