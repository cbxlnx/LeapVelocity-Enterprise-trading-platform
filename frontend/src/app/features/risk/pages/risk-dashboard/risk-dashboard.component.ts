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
import { RiskDashboardMockService } from '../../services/risk-dashboard-mock.service';
import { RiskDashboardData, RiskHorizon, RiskPortfolio, ShockResult, ShockValues, ScenarioWaterfall } from '../../models/risk-dashboard.model';
import { calculateCustomShock, calculateMaximumDrawdown, getVaRForHorizon, reconcileWaterfall } from '../../utils/risk-calculations';

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
  private readonly service = inject(RiskDashboardMockService);
  private readonly destroyRef = inject(DestroyRef);
  readonly math = Math;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly data = signal<RiskDashboardData | null>(null);
  readonly selectedAccountId = signal('ACC-001');
  readonly historicalWindow = signal<'1Y' | '6M' | '3M'>('1Y');
  readonly horizon = signal<RiskHorizon>(1);
  readonly customShocks = signal<ShockValues>({ ...BASELINE_SHOCKS });
  readonly customResult = signal<ShockResult | null>(null);

  readonly accounts = computed(() => this.data()?.portfolios ?? []);
  readonly basePortfolio = computed(() => this.accounts().find((portfolio) => portfolio.accountId === this.selectedAccountId()) ?? this.accounts()[0] ?? null);
  readonly portfolio = computed(() => this.forWindow(this.basePortfolio()));
  readonly selectedVaR = computed(() => {
    const current = this.portfolio();
    return current ? getVaRForHorizon(current.oneDayVar, this.horizon()) : null;
  });
  readonly scenarioPortfolio = computed(() => {
    const current = this.portfolio();
    if (!current) return null;
    const result = this.currentShockResult(current);
    return {
      ...current,
      scenarios: current.scenarios.map((scenario) => scenario.kind === 'custom' ? {
        ...scenario,
        lossUsd: result.lossUsd,
        lossPercent: result.impactPercent,
        period: `Market ${this.customShocks().market}% · Sector increments 0%`,
      } : scenario),
    };
  });
  readonly waterfalls = computed(() => {
    const current = this.portfolio();
    if (!current) return [];
    const result = this.currentShockResult(current);
    const deltas = current.holdings.map((holding) => ({
      label: holding.symbol,
      deltaUsd: -holding.marketValue * ((this.customShocks().market + this.customShocks()[holding.sector]) / 100),
    }));
    const custom: ScenarioWaterfall = {
      title: 'Custom shock',
      period: 'Current positions · User-defined shock',
      startingEquityUsd: current.portfolioValueUsd,
      deltas,
      endingEquityUsd: result.postShockEquityUsd,
      cashReserveUsd: current.cashReserveUsd,
    };
    return [...current.historicalWaterfalls, custom];
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
        if (!data.portfolios.some((portfolio) => portfolio.accountId === this.selectedAccountId())) {
          this.selectedAccountId.set(data.portfolios[0]?.accountId ?? '');
        }
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

  runCustomShock(event: { shocks: ShockValues; result: ShockResult }): void {
    this.customShocks.set({ ...event.shocks });
    this.customResult.set({ ...event.result });
  }

  editShock(): void {
    this.customShocks.set({ ...this.customShocks() });
  }

  private currentShockResult(portfolio: RiskPortfolio): ShockResult {
    return this.customResult() ?? calculateCustomShock(portfolio.holdings, portfolio.portfolioValueUsd, this.customShocks());
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
      dailyReturnCount: Math.round(portfolio.dailyReturnCount * drawdownSeries.length / portfolio.drawdownSeries.length),
      drawdownSeries,
    };
  }
}
