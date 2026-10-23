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
import { HistoricalLookback, RiskDashboardData, RiskHorizon, RiskPortfolio, ShockResult, ShockValues } from '../../models/risk-dashboard.model';
import { calculateCustomShock } from '../../utils/risk-calculations';

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
  readonly data = signal<RiskDashboardData | null>(null);
  readonly historicalWindow = signal<HistoricalLookback>('1Y');
  readonly horizon = signal<RiskHorizon>(1);
  readonly customShocks = signal<ShockValues>({ ...BASELINE_SHOCKS });
  readonly customResult = signal<ShockResult | null>(null);
  readonly historicalWaterfallIds = ['waterfall-stress-2008', 'waterfall-stress-2020'];
  readonly customWaterfallIds = ['waterfall-custom-shock'];

  readonly portfolio = computed(() => this.data()?.portfolios[0] ?? null);
  readonly hasPositions = computed(() => (this.portfolio()?.holdings.length ?? 0) > 0);
  readonly selectedVaR = computed(() => {
    const current = this.portfolio();
    return current?.varByHorizon?.[this.horizon()] ?? (this.horizon() === 1 ? current?.oneDayVar ?? null : null);
  });
  readonly historicalPortfolio = computed(() => {
    const current = this.portfolio();
    if (!current) return null;
    return {
      ...current,
      scenarios: current.scenarios.filter((scenario) => scenario.kind === 'historical'),
    };
  });
  readonly historicalWaterfalls = computed(() => this.portfolio()?.historicalWaterfalls ?? []);
  readonly customWaterfalls = computed(() => this.customResult()?.waterfall ? [this.customResult()!.waterfall!] : []);
  readonly insights = computed(() => {
    const current = this.portfolio();
    return current ? this.buildInsights(current) : [];
  });

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(lookbackDays = this.lookbackDaysFor(this.historicalWindow())): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getDashboard(lookbackDays).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.data.set(data);
        this.customResult.set(null);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Risk estimates could not be loaded. Please retry.');
      },
    });
  }

  setHistoricalWindow(window: HistoricalLookback): void {
    this.historicalWindow.set(window);
    this.loadDashboard(this.lookbackDaysFor(window));
  }

  updateHorizon(horizon: RiskHorizon): void {
    this.horizon.set(horizon);
  }

  runCustomShock(shocks: ShockValues): void {
    const current = this.portfolio();
    if (!current || current.portfolioValueUsd === null) return;
    this.customShocks.set({ ...shocks });
    this.error.set(null);
    this.customResult.set(calculateCustomShock(current.holdings, current.portfolioValueUsd, shocks, current.cashReserveUsd));
  }

  focusBreakdown(scenarioId: string): void {
    if (typeof document === 'undefined') return;
    const target = document.getElementById(`waterfall-${scenarioId}`);
    if (!target) return;
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target.focus({ preventScroll: true });
  }

  private lookbackDaysFor(window: HistoricalLookback): number {
    return { '1Y': 252, '3Y': 756, '5Y': 1260, '10Y': 2520, MAX: 10000 }[window];
  }

  coverageSummary(): string {
    const current = this.portfolio();
    return current ? `${current.dailyReturnCount} aligned observations · ${current.coverage?.missingSymbols.length ?? 0} holdings missing history` : 'No history loaded';
  }

  private buildInsights(portfolio: RiskPortfolio): RiskPortfolio['insights'] {
    const existing = portfolio.insights.filter((insight) => !['Cash reserve', 'Historical data coverage'].includes(insight.label));
    const cashValue = portfolio.portfolioValueUsd;
    const cashPercent = cashValue === null || cashValue === 0 ? null : portfolio.cashReserveUsd / cashValue * 100;
    const requestedDays = this.lookbackDaysFor(this.historicalWindow());
    const coveragePercent = this.historicalWindow() === 'MAX' ? null : Math.min(100, portfolio.dailyReturnCount / requestedDays * 100);
    const missingSymbols = portfolio.coverage?.missingSymbols ?? [];
    const coverageDetail = `${portfolio.sectionStatus?.['historicalRisk']?.status ?? 'READY'} · Usable ${portfolio.historicalFrom} to ${portfolio.historicalTo}${missingSymbols.length ? ` · Missing: ${missingSymbols.join(', ')}` : ` · ${portfolio.holdings.length} held symbols aligned`}`;
    return [
      {
        label: 'Cash reserve',
        value: cashPercent === null ? 'Unavailable' : `${cashPercent.toFixed(1)}%`,
        detail: cashValue === null ? 'Current account equity is unavailable.' : `$${portfolio.cashReserveUsd.toLocaleString('en-US')} cash of $${cashValue.toLocaleString('en-US')} total account equity.`,
      },
      {
        label: 'Historical data coverage',
        value: coveragePercent === null ? `${portfolio.dailyReturnCount} aligned obs` : `${coveragePercent.toFixed(0)}% · ${portfolio.dailyReturnCount} obs`,
        detail: coverageDetail,
      },
      ...existing,
    ];
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
