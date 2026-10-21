import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { RiskPortfolio, ShockResult, ShockValues } from '../../models/risk-dashboard.model';
import { calculateCustomShock, formatAmount } from '../../utils/risk-calculations';
@Component({ selector: 'app-custom-shock', standalone: true, templateUrl: './custom-shock.component.html',
  styleUrl: './custom-shock.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class CustomShockComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly shockRun = output<ShockResult | null>();
  readonly shocks = signal<ShockValues>({ market: 0 });
  readonly result = signal<ShockResult | null>(null);
  readonly error = signal<string | null>(null);
  readonly format = formatAmount;
  updateShock(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    const value = text.trim() === '' ? NaN : Number(text);
    this.shocks.set({ market: value });
    this.error.set(!Number.isFinite(value) || Math.abs(value) > 50 ? 'Use a finite price change from −50% to +50%.' : null);
    this.result.set(null);
    this.shockRun.emit(null);
  }
  reset(): void { this.shocks.set({ market: 0 }); this.error.set(null); this.result.set(null); this.shockRun.emit(null); }
  run(): void {
    const result = calculateCustomShock(this.portfolio(), this.shocks());
    this.result.set(result);
    this.error.set(result ? null : 'A valid invested valuation and a price change from −50% to +50% are required.');
    this.shockRun.emit(result);
  }
}
