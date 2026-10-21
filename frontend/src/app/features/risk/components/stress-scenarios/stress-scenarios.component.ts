import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
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
  readonly editShock = output<void>();
  readonly expandedScenario = signal<string | null>(null);

  toggleDetails(id: string): void {
    this.expandedScenario.update((current) => current === id ? null : id);
  }
}
