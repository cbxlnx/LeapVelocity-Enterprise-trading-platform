import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, timeout } from 'rxjs';
import { PriceChartComponent } from '../../components/price-chart/price-chart.component';
import { HistoricalPrices } from '../../models/historical-prices.model';
import { defaultHistoryRange, formatTradingDate } from '../../models/trading-date';
import { AnalyticsApiService } from '../../services/analytics-api.service';
import { AuthService } from '../../../../services/auth.service';
import { InstrumentService } from '../../../../services/instrument.service';
import { Instrument } from '../../../../models/api.models';
import { PortfolioSummary } from '../../models/portfolio-summary.model';
import { PortfolioSummaryComponent } from '../../components/portfolio-summary/portfolio-summary.component';
import { PerformancePanelComponent } from '../../components/performance-panel/performance-panel.component';
import { PerformancePeriod, PortfolioPerformance } from '../../models/portfolio-performance.model';

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [CurrencyPipe, ReactiveFormsModule, PriceChartComponent, PortfolioSummaryComponent, PerformancePanelComponent],
  templateUrl: './analytics-dashboard.component.html',
  styleUrl: './analytics-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsDashboardComponent implements OnInit {
  private readonly api = inject(AnalyticsApiService);
  private readonly auth = inject(AuthService);
  private readonly instrumentService = inject(InstrumentService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  private summaryRequest?: Subscription;
  private performanceRequest?: Subscription;
  private instrumentsRequest?: Subscription;
  private readonly initialRange = defaultHistoryRange();
  readonly formatDate = formatTradingDate;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly history = signal<HistoricalPrices | null>(null);
  readonly summary = signal<PortfolioSummary | null>(null);
  readonly summaryLoading = signal(false);
  readonly summaryError = signal<string | null>(null);
  readonly performance = signal<PortfolioPerformance | null>(null);
  readonly performancePeriod = signal<PerformancePeriod>('1Y');
  readonly performanceLoading = signal(false);
  readonly performanceError = signal<string | null>(null);
  readonly instruments = signal<Instrument[]>([]);
  readonly instrumentsLoading = signal(false);
  readonly instrumentsError = signal<string | null>(null);
  readonly dateForm = new FormGroup({
    symbol: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    from: new FormControl(this.initialRange.from, { nonNullable: true, validators: [Validators.required] }),
    to: new FormControl(this.initialRange.to, { nonNullable: true, validators: [Validators.required] }),
  }, { validators: (form) => {
    const { from, to } = form.value;
    return from && to && from > to ? { dateOrder: true } : null;
  } });
  private readonly selectedSymbol = signal('');
  readonly selectedInstrument = computed(() => this.instruments().find(instrument => instrument.symbol === this.selectedSymbol()));

  ngOnInit(): void {
    this.refreshPortfolio();
    this.dateForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ symbol }) => {
      if (symbol === this.selectedSymbol()) return;
      this.selectedSymbol.set(symbol ?? '');
      this.request?.unsubscribe();
      this.history.set(null);
      this.error.set(null);
      this.loading.set(false);
      this.loadPrices();
    });
    this.loadInstruments();
  }

  loadInstruments(): void {
    this.instrumentsRequest?.unsubscribe();
    this.instrumentsLoading.set(true);
    this.instrumentsError.set(null);
    this.instrumentsRequest = this.instrumentService.getInstruments().pipe(
      timeout(15000), takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (instruments) => {
        const eligible = instruments.filter(instrument => instrument.tradable && instrument.currency === 'USD'
          && instrument.assetClass !== 'CASH').sort((a, b) => a.symbol.localeCompare(b.symbol));
        this.instruments.set(eligible);
        this.instrumentsLoading.set(false);
        const current = this.dateForm.controls.symbol.value;
        this.dateForm.controls.symbol.setValue(eligible.find(instrument => instrument.symbol === current)?.symbol
          ?? eligible[0]?.symbol ?? '');
      },
      error: (error: Error) => {
        this.instrumentsLoading.set(false);
        this.instrumentsError.set(error.name === 'TimeoutError'
          ? 'The instrument list took too long to load. Please retry.' : error.message || 'Unable to load instruments. Please retry.');
      },
    });
  }

  refreshPortfolio(): void {
    this.loadSummary();
    this.loadPerformance();
  }

  changePerformancePeriod(period: PerformancePeriod): void {
    this.performancePeriod.set(period);
    this.loadPerformance();
  }

  loadPerformance(): void {
    this.performanceRequest?.unsubscribe();
    this.performance.set(null);
    this.performanceError.set(null);
    const accountId = this.auth.getAccountId();
    if (!accountId) {
      this.performanceLoading.set(false);
      this.performanceError.set('Your trading account is unavailable. Please sign in again.');
      return;
    }
    this.performanceLoading.set(true);
    this.performanceRequest = this.api.getPerformance(accountId, this.performancePeriod()).pipe(
      timeout(15000), takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (performance) => {
        this.performance.set(performance);
        this.performanceLoading.set(false);
      },
      error: (error: Error) => {
        this.performanceError.set(error.name === 'TimeoutError'
          ? 'The simulation API took too long to respond. Please retry.' : error.message);
        this.performanceLoading.set(false);
      },
    });
  }

  loadSummary(): void {
    this.summaryRequest?.unsubscribe();
    this.summary.set(null);
    this.summaryError.set(null);
    const accountId = this.auth.getAccountId();
    if (!accountId) {
      this.summaryLoading.set(false);
      this.summaryError.set('Your trading account is unavailable. Please sign in again.');
      return;
    }
    this.summaryLoading.set(true);
    this.summaryRequest = this.api.getSummary(accountId).pipe(
      timeout(15000), takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.summaryLoading.set(false);
      },
      error: (error: Error) => {
        this.summaryError.set(error.name === 'TimeoutError'
          ? 'The portfolio API took too long to respond. Please retry.' : error.message);
        this.summaryLoading.set(false);
      },
    });
  }

  loadPrices(): void {
    if (this.dateForm.invalid || !this.selectedInstrument() || this.instrumentsLoading()) {
      this.dateForm.markAllAsTouched();
      return;
    }
    this.request?.unsubscribe();
    this.history.set(null);
    this.error.set(null);
    this.loading.set(true);
    const { symbol, from, to } = this.dateForm.getRawValue();
    this.request = this.api.getPrices(symbol, from, to).pipe(
      timeout(15000),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (history) => {
        this.history.set(history);
        this.loading.set(false);
      },
      error: (error: Error) => {
        this.error.set(error.name === 'TimeoutError'
          ? 'The API took too long to respond. Please retry.'
          : error.message || 'Unable to load historical prices. Please retry.');
        this.loading.set(false);
      },
    });
  }
}
