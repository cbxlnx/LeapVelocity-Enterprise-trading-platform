import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
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
  readonly impactScale = computed(() => {
    const largestImpact = Math.max(...this.portfolio().scenarios.map((scenario) => Math.abs(scenario.lossPercent ?? 0)), 0);
    return Math.max(20, Math.ceil((largestImpact * 1.1) / 5) * 5);
  });

  currency(value: number | null): string {
    return value === null ? 'Unavailable' : value.toLocaleString('en-US');
  }

  metric(value: number | null): string {
    return value === null ? 'Unavailable' : value.toFixed(2);
  }

  impactWidth(value: number | null): number {
    return value === null ? 0 : Math.abs(value) / this.impactScale() * 100;
  }

  isSevereImpact(value: number | null): boolean {
    return value !== null && Math.abs(value) >= this.impactScale() * 0.25;
  }
}
