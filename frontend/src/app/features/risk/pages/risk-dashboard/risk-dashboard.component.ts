import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, catchError, forkJoin, of, timeout } from 'rxjs';
import { AuthService } from '../../../../services/auth.service';
import { InstrumentService } from '../../../../services/instrument.service';
import { Instrument } from '../../../../models/api.models';
import { AnalyticsApiService } from '../../../analytics/services/analytics-api.service';
import { PortfolioRisk, RiskScalarMetric, RiskVarResult } from '../../../analytics/models/portfolio-risk.model';
import { PerformancePeriod } from '../../../analytics/models/portfolio-performance.model';
import { RiskContributionComponent } from '../../components/risk-contribution/risk-contribution.component';
import { ConcentrationComponent } from '../../components/concentration/concentration.component';
import { CustomShockComponent } from '../../components/custom-shock/custom-shock.component';
import { DistributionDrawdownComponent } from '../../components/distribution-drawdown/distribution-drawdown.component';
import { ScenarioWaterfallComponent } from '../../components/scenario-waterfall/scenario-waterfall.component';
import { StressScenariosComponent } from '../../components/stress-scenarios/stress-scenarios.component';
import { VarModelsComponent } from '../../components/var-models/var-models.component';
import { RiskHorizon, ScenarioBreakdown, ShockResult } from '../../models/risk-dashboard.model';
import { formatAmount, toRiskPortfolio } from '../../utils/risk-calculations';

@Component({
  selector: 'app-risk-dashboard', standalone: true,
  imports: [ConcentrationComponent, CustomShockComponent, DistributionDrawdownComponent, RiskContributionComponent,
    ScenarioWaterfallComponent, StressScenariosComponent, VarModelsComponent],
  templateUrl: './risk-dashboard.component.html', styleUrl: './risk-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskDashboardComponent implements OnInit {
  private readonly api = inject(AnalyticsApiService);
  private readonly auth = inject(AuthService);
  private readonly instruments = inject(InstrumentService);
  private readonly destroyRef = inject(DestroyRef);
  private riskRequest?: Subscription;
  readonly accountId = this.auth.getAccountId();
  readonly risk = signal<PortfolioRisk | null>(null);
  readonly metadata = signal<Instrument[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly historicalWindow = signal<PerformancePeriod>('1Y');
  readonly horizon = signal<RiskHorizon>(1);
  readonly customResult = signal<ShockResult | null>(null);
  readonly selectedScenario = signal<ScenarioBreakdown | null>(null);
  readonly riskVars = computed(() => ({
    historical: this.risk()?.valueAtRisk.find(value => value.method === 'HISTORICAL'),
    parametric: this.risk()?.valueAtRisk.find(value => value.method === 'PARAMETRIC'),
    monteCarlo: this.risk()?.valueAtRisk.find(value => value.method === 'MONTE_CARLO'),
  }));
  readonly portfolio = computed(() => {
    const risk = this.risk();
    return risk ? toRiskPortfolio(risk, this.metadata()) : null;
  });
  readonly waterfalls = computed(() => {
    const result = this.selectedScenario();
    return result ? [result.waterfall] : [];
  });

  ngOnInit(): void { this.loadDashboard(); }

  loadDashboard(): void {
    this.riskRequest?.unsubscribe();
    this.risk.set(null);
    this.customResult.set(null);
    this.selectedScenario.set(null);
    this.error.set(null);
    this.loading.set(false);
    if (!this.accountId) { this.error.set('The signed-in account could not be identified. Sign in again.'); return; }
    this.loading.set(true);
    this.riskRequest = forkJoin({
      risk: this.api.getRisk(this.accountId, this.historicalWindow(), { confidenceLevel: .95, holdingPeriodDays: this.horizon() }),
      metadata: this.instruments.getInstruments().pipe(timeout(15000), catchError(() => of(null))),
    }).pipe(timeout(15000), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ risk, metadata }) => {
        this.loading.set(false);
        if (risk.valuation.accountId !== this.accountId || !risk.series) {
          this.error.set('The account assessment response is incompatible. Refresh after updating the backend.'); return;
        }
        this.metadata.set(metadata);
        this.risk.set(risk);
      },
      error: () => { this.loading.set(false); this.error.set('Account risk data could not be loaded. Please retry.'); },
    });
  }

  updateHorizon(horizon: RiskHorizon): void {
    if (horizon !== 1 && horizon !== 10) { this.error.set('Supported VaR holding periods are 1 or 10 trading days.'); return; }
    this.horizon.set(horizon);
    this.loadDashboard();
  }
  updateWindow(period: PerformancePeriod): void { this.historicalWindow.set(period); this.loadDashboard(); }
  showCustomResult(result: ShockResult | null): void { this.customResult.set(result); this.selectedScenario.set(result); }
  formatRisk(value: number | null | undefined, prefix = '', suffix = ''): string { return formatAmount(value, prefix, suffix); }
  riskReason(result: RiskScalarMetric | RiskVarResult | undefined): string {
    return result && result.status !== 'READY' ? result.reasons.join(' · ') : '';
  }
}
