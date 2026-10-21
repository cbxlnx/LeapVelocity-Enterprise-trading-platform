import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timeout } from 'rxjs';
import { AnalyticsApiService } from '../../../analytics/services/analytics-api.service';
import { ShockResult, ShockValues } from '../../models/risk-dashboard.model';
import { toShockResult, formatAmount } from '../../utils/risk-calculations';
@Component({ selector: 'app-custom-shock', standalone: true, templateUrl: './custom-shock.component.html',
  styleUrl: './custom-shock.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class CustomShockComponent {
  private readonly api = inject(AnalyticsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private pending?: Subscription;
  readonly accountId = input.required<number>();
  readonly disabledReason = input<string | null>(null);
  readonly shockRun = output<ShockResult | null>();
  readonly shocks = signal<ShockValues>({ market: 0 });
  readonly result = signal<ShockResult | null>(null);
  readonly loading = signal(false);
  readonly unavailable = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly format = formatAmount;
  private clear(): void {
    this.pending?.unsubscribe();
    this.loading.set(false);
    this.result.set(null);
    this.unavailable.set(null);
    this.shockRun.emit(null);
  }
  updateShock(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    const value = text.trim() === '' ? NaN : Number(text);
    this.clear();
    this.shocks.set({ market: value });
    this.error.set(!Number.isFinite(value) || Math.abs(value) > 50 ? 'Use a finite price change from −50% to +50%.' : null);
  }
  reset(): void { this.clear(); this.shocks.set({ market: 0 }); this.error.set(null); }
  run(): void {
    const change = this.shocks().market;
    this.clear();
    if (this.disabledReason()) return;
    if (!Number.isFinite(change) || Math.abs(change) > 50) {
      this.error.set('Use a finite price change from −50% to +50%.'); return;
    }
    this.error.set(null);
    this.loading.set(true);
    this.pending = this.api.calculateShock(this.accountId(), change)
      .pipe(timeout(15000), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: response => {
          this.loading.set(false);
          if (response.valuation.accountId !== this.accountId() || response.priceChangePercent !== change) {
            this.error.set('The price shock response does not match the requested account or shock.'); return;
          }
          const result = toShockResult(response);
          if (response.status === 'READY' && !result) {
            this.error.set('The price shock response is incomplete.'); return;
          }
          this.result.set(result);
          this.unavailable.set(result ? null : `${response.status} · ${response.reasons.join(' · ')}`);
          this.shockRun.emit(result);
        },
        error: () => { this.loading.set(false); this.error.set('Price shock calculation failed. Please retry.'); },
      });
  }
}
