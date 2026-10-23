import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { BASELINE_SHOCKS } from '../../data/risk-dashboard.mock';
import { RiskPortfolio, ShockResult, ShockValues } from '../../models/risk-dashboard.model';
import { calculateCustomShock } from '../../utils/risk-calculations';

@Component({
  selector: 'app-custom-shock',
  standalone: true,
  templateUrl: './custom-shock.component.html',
  styleUrl: './custom-shock.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomShockComponent {
  readonly math = Math;
  readonly portfolio = input.required<RiskPortfolio>();
  readonly preset = input.required<ShockValues>();
  readonly shockRun = output<ShockValues>();
  readonly shocks = signal<ShockValues>({ ...BASELINE_SHOCKS });
  readonly projectedResult = computed<ShockResult | null>(() => {
    const portfolioValue = this.portfolio().portfolioValueUsd;
    return portfolioValue === null ? null : calculateCustomShock(this.portfolio().holdings, portfolioValue, this.shocks(), this.portfolio().cashReserveUsd);
  });
  readonly controls: Array<{ key: keyof ShockValues; label: string; min: number; max: number }> = [
    { key: 'market', label: 'Market', min: -50, max: 50 },
    { key: 'Technology', label: 'Technology', min: -30, max: 30 },
    { key: 'Financials', label: 'Financials', min: -30, max: 30 },
    { key: 'Energy', label: 'Energy', min: -30, max: 30 },
    { key: 'Healthcare', label: 'Healthcare', min: -30, max: 30 },
  ];

  constructor() {
    effect(() => this.shocks.set({ ...this.preset() }));
  }

  updateShock(key: keyof ShockValues, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.shocks.update((current) => ({ ...current, [key]: value }));
  }

  reset(): void {
    this.shocks.set({ market: 0, Technology: 0, Financials: 0, Energy: 0, Healthcare: 0 });
  }

  run(): void {
    this.shockRun.emit({ ...this.shocks() });
  }

  impactLabel(): string {
    const result = this.projectedResult();
    return result ? `${result.lossUsd >= 0 ? '−' : '+'}$${Math.abs(result.lossUsd).toLocaleString('en-US')}` : 'Unavailable';
  }

  impactPercentLabel(): string {
    const result = this.projectedResult();
    return result ? `${result.impactPercent >= 0 ? '−' : '+'}${Math.abs(result.impactPercent).toFixed(2)}% projected` : 'Unavailable';
  }

  postShockLabel(): string {
    const value = this.projectedResult()?.postShockEquityUsd;
    return value === undefined ? 'Unavailable' : `$${value.toLocaleString('en-US')}`;
  }

  formatShock(value: number): string {
    return `${value > 0 ? '+' : ''}${value}%`;
  }
}
