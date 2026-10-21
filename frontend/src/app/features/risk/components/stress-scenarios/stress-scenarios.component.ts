import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timeout } from 'rxjs';
import { ScenarioBreakdown, ShockResult } from '../../models/risk-dashboard.model';
import { HistoricalReplay, ScenarioAvailability, StressCapabilities } from '../../../analytics/models/portfolio-risk.model';
import { AnalyticsApiService } from '../../../analytics/services/analytics-api.service';
import { formatAmount, toReplayBreakdown } from '../../utils/risk-calculations';
@Component({ selector: 'app-stress-scenarios', standalone: true, templateUrl: './stress-scenarios.component.html',
  styleUrl: './stress-scenarios.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class StressScenariosComponent {
  private readonly api = inject(AnalyticsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private pending?: Subscription;
  readonly accountId = input.required<number>();
  readonly customResult = input<ShockResult | null>(null);
  readonly capabilities = input<StressCapabilities>();
  readonly replayRun = output<ScenarioBreakdown | null>();
  readonly results = signal<Record<string, HistoricalReplay>>({});
  readonly errors = signal<Record<string, string>>({});
  readonly loadingId = signal<string | null>(null);
  readonly scenarios = computed<ScenarioAvailability[]>(() => this.capabilities()?.historical ?? [
    { id: 'CRISIS_2008', title: '2008 financial crisis · calendar year', status: 'UNAVAILABLE', reasons: ['Select Run replay to validate stored endpoints and identity coverage.'] },
    { id: 'MARCH_2020', title: 'March 2020 market stress', status: 'UNAVAILABLE', reasons: ['Select Run replay to validate stored endpoints and identity coverage.'] },
  ]);
  readonly format = formatAmount;
  run(id: string): void {
    this.pending?.unsubscribe();
    this.results.update(results => Object.fromEntries(Object.entries(results).filter(([key]) => key !== id)));
    this.errors.update(errors => ({ ...errors, [id]: '' }));
    this.loadingId.set(id);
    this.replayRun.emit(null);
    this.pending = this.api.replayHistory(this.accountId(), id).pipe(timeout(15000), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        this.loadingId.set(null);
        if (result.valuation.accountId !== this.accountId() || result.scenario.id !== id) {
          this.errors.update(errors => ({ ...errors, [id]: 'The replay response does not match the requested account or scenario.' })); return;
        }
        const breakdown = toReplayBreakdown(result);
        if (result.status === 'READY' && !breakdown) {
          this.errors.update(errors => ({ ...errors, [id]: 'The replay response has incomplete holding coverage.' })); return;
        }
        this.results.update(results => ({ ...results, [id]: result }));
        this.replayRun.emit(breakdown);
      },
      error: () => {
        this.loadingId.set(null);
        this.errors.update(errors => ({ ...errors, [id]: 'Historical replay calculation failed. Please retry.' }));
      },
    });
  }
}
