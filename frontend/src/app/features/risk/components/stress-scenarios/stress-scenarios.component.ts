import { ChangeDetectionStrategy, Component, DestroyRef, OnChanges, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timeout } from 'rxjs';
import { ScenarioBreakdown } from '../../models/risk-dashboard.model';
import { HistoricalReplay, StressCapabilities } from '../../../analytics/models/portfolio-risk.model';
import { AnalyticsApiService } from '../../../analytics/services/analytics-api.service';
import { formatAmount, toReplayBreakdown } from '../../utils/risk-calculations';
@Component({ selector: 'app-stress-scenarios', standalone: true, templateUrl: './stress-scenarios.component.html',
  styleUrl: './stress-scenarios.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class StressScenariosComponent implements OnChanges {
  private readonly api = inject(AnalyticsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pending = new Map<string, Subscription>();
  readonly accountId = input.required<number>();
  readonly capabilities = input<StressCapabilities>();
  readonly replayRun = output<ScenarioBreakdown | null>();
  readonly results = signal<Record<string, HistoricalReplay>>({});
  readonly errors = signal<Record<string, string>>({});
  readonly loadingIds = signal<string[]>([]);
  // The existing read-only replay route validates availability for this account.
  // Static capability metadata alone cannot establish complete price/identity coverage.
  readonly scenarios = computed(() => (this.capabilities()?.historical ?? [
    { id: 'CRISIS_2008', title: '2008 financial crisis · calendar year' },
    { id: 'MARCH_2020', title: 'March 2020 market stress' },
  ]).filter(scenario => !('status' in scenario) || scenario.status !== 'NOT_IMPLEMENTED'));
  readonly readyResults = computed(() => this.scenarios().flatMap(scenario => {
    const result = this.results()[scenario.id];
    return result ? [result] : [];
  }));
  readonly failures = computed(() => this.scenarios().filter(scenario => this.errors()[scenario.id]));
  readonly format = formatAmount;

  ngOnChanges(): void {
    this.pending.forEach(request => request.unsubscribe());
    this.pending.clear();
    this.results.set({});
    this.errors.set({});
    this.loadingIds.set([]);
    this.scenarios().forEach(scenario => this.run(scenario.id, false));
  }

  select(result: HistoricalReplay): void { this.replayRun.emit(toReplayBreakdown(result)); }

  run(id: string, select = true): void {
    this.pending.get(id)?.unsubscribe();
    this.results.update(results => Object.fromEntries(Object.entries(results).filter(([key]) => key !== id)));
    this.errors.update(errors => ({ ...errors, [id]: '' }));
    this.loadingIds.update(ids => [...ids.filter(value => value !== id), id]);
    if (select) this.replayRun.emit(null);
    this.pending.set(id, this.api.replayHistory(this.accountId(), id).pipe(timeout(15000), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        this.loadingIds.update(ids => ids.filter(value => value !== id));
        if (result.valuation.accountId !== this.accountId() || result.scenario.id !== id) {
          this.errors.update(errors => ({ ...errors, [id]: 'The replay response does not match the requested account or scenario.' })); return;
        }
        const breakdown = toReplayBreakdown(result);
        if (result.status === 'READY' && !breakdown) {
          this.errors.update(errors => ({ ...errors, [id]: 'The replay response has incomplete holding coverage.' })); return;
        }
        // Missing history or an unsupported holding hides the entire scenario;
        // individually validated rows must never become a partial portfolio total.
        if (breakdown) this.results.update(results => ({ ...results, [id]: result }));
        if (select) this.replayRun.emit(breakdown);
      },
      error: () => {
        this.loadingIds.update(ids => ids.filter(value => value !== id));
        this.errors.update(errors => ({ ...errors, [id]: 'Historical replay calculation failed. Please retry.' }));
      },
    }));
  }
}
