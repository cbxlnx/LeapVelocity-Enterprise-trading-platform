import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BASELINE_SHOCKS } from '../../data/risk-dashboard.mock';
import { RiskContributionComponent } from '../../components/risk-contribution/risk-contribution.component';
import { ConcentrationComponent } from '../../components/concentration/concentration.component';
import { CustomShockComponent } from '../../components/custom-shock/custom-shock.component';
import { DistributionDrawdownComponent } from '../../components/distribution-drawdown/distribution-drawdown.component';
import { ScenarioWaterfallComponent } from '../../components/scenario-waterfall/scenario-waterfall.component';
import { StressScenariosComponent } from '../../components/stress-scenarios/stress-scenarios.component';
import { VarModelsComponent } from '../../components/var-models/var-models.component';
import { RiskDashboardApiService } from '../../services/risk-dashboard-api.service';
import { RiskDashboardData, RiskHorizon, RiskPortfolio, ShockResult, ShockValues } from '../../models/risk-dashboard.model';
import { calculateMaximumDrawdown } from '../../utils/risk-calculations';

@Component({
  selector: 'app-risk-dashboard',
  standalone: true,
  imports: [
    ConcentrationComponent,
    CustomShockComponent,
    DistributionDrawdownComponent,
    RiskContributionComponent,
    ScenarioWaterfallComponent,
    StressScenariosComponent,
    VarModelsComponent,
  ],
  templateUrl: './risk-dashboard.component.html',
  styleUrl: './risk-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskDashboardComponent implements OnInit {
  private readonly service = inject(RiskDashboardApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly math = Math;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly shockLoading = signal(false);
  readonly data = signal<RiskDashboardData | null>(null);
  readonly selectedAccountId = signal('');
  readonly historicalWindow = signal<'1Y' | '6M' | '3M'>('1Y');
  readonly horizon = signal<RiskHorizon>(1);
  readonly customShocks = signal<ShockValues>({ ...BASELINE_SHOCKS });
  readonly customResult = signal<ShockResult | null>(null);

  readonly accounts = computed(() => this.data()?.portfolios ?? []);
  readonly basePortfolio = computed(() => this.accounts().find((portfolio) => portfolio.accountId === this.selectedAccountId()) ?? this.accounts()[0] ?? null);
  readonly portfolio = computed(() => this.forWindow(this.basePortfolio()));
  readonly selectedVaR = computed(() => {
    const current = this.portfolio();
    return current?.varByHorizon?.[this.horizon()] ?? (this.horizon() === 1 ? current?.oneDayVar ?? null : null);
  });
  readonly scenarioPortfolio = computed(() => {
    const current = this.portfolio();
    if (!current) return null;
    const result = this.customResult();
    const customScenario = result?.waterfall ? {
      id: 'custom-shock',
      title: 'Custom shock',
      period: 'Current positions',
      description: 'Backend-calculated market and sector increments.',
      kind: 'custom' as const,
      lossUsd: result.lossUsd,
      lossPercent: result.impactPercent,
      waterfall: result.waterfall,
    } : null;
    return {
      ...current,
      scenarios: [...current.scenarios.filter((scenario) => scenario.kind !== 'custom'), ...(customScenario ? [customScenario] : [])],
    };
  });
  readonly waterfalls = computed(() => {
    const current = this.portfolio();
    if (!current) return [];
    return [...current.historicalWaterfalls, ...(this.customResult()?.waterfall ? [this.customResult()!.waterfall!] : [])];
  });

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getDashboard().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.data.set(data);
        this.selectedAccountId.set(data.portfolios[0]?.accountId ?? '');
        this.customResult.set(null);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Risk estimates could not be loaded. Please retry.');
      },
    });
  }

  selectAccount(accountId: string): void {
    this.selectedAccountId.set(accountId);
    this.customShocks.set({ ...BASELINE_SHOCKS });
    this.customResult.set(null);
  }

  updateHorizon(horizon: RiskHorizon): void {
    this.horizon.set(horizon);
  }

  runCustomShock(shocks: ShockValues): void {
    this.customShocks.set({ ...shocks });
    this.shockLoading.set(true);
    this.error.set(null);
    this.service.runCustomShock(shocks).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.customResult.set(result);
        this.shockLoading.set(false);
      },
      error: (error: Error) => {
        this.shockLoading.set(false);
        this.error.set(error.message);
      },
    });
  }

  editShock(): void {
    this.customShocks.set({ ...this.customShocks() });
  }

  private forWindow(portfolio: RiskPortfolio | null): RiskPortfolio | null {
    if (!portfolio) return null;
    const count = this.historicalWindow() === '3M' ? 7 : this.historicalWindow() === '6M' ? 14 : portfolio.drawdownSeries.length;
    const drawdownSeries = portfolio.drawdownSeries.slice(-count);
    const maximumDrawdownPercent = calculateMaximumDrawdown(drawdownSeries.map((point) => point.percent));
    const trough = drawdownSeries.find((point) => point.percent === maximumDrawdownPercent);
    return {
      ...portfolio,
      historicalFrom: drawdownSeries[0]?.date ?? portfolio.historicalFrom,
      maximumDrawdownPercent,
      maximumDrawdownDate: trough?.date ?? portfolio.maximumDrawdownDate,
      dailyReturnCount: portfolio.drawdownSeries.length === 0 ? 0 : Math.round(portfolio.dailyReturnCount * drawdownSeries.length / portfolio.drawdownSeries.length),
      drawdownSeries,
    };
  }

  formatCurrency(value: number | null | undefined, digits = 2): string {
    return value === null || value === undefined ? 'Unavailable' : value.toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }

  formatMetric(value: number | null | undefined, digits = 2): string {
    return value === null || value === undefined ? 'Unavailable' : value.toFixed(digits);
  }

  ratioPercent(value: number | null | undefined, total: number | null | undefined): string {
    return value === null || value === undefined || total === null || total === undefined || total === 0 ? 'Unavailable' : (value / total * 100).toFixed(2);
  }
}
