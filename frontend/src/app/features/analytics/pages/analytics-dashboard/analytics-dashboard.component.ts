import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, forkJoin, timeout } from 'rxjs';
import { PriceChartComponent } from '../../components/price-chart/price-chart.component';
import { ComparisonChartComponent } from '../../components/comparison-chart/comparison-chart.component';
import { PerformancePanelComponent } from '../../components/performance-panel/performance-panel.component';
import { HistoricalPrices } from '../../models/historical-prices.model';
import { defaultHistoryRange, formatTradingDate } from '../../models/trading-date';
import { AnalyticsApiService } from '../../services/analytics-api.service';
import { AuthService } from '../../../../services/auth.service';
import { InstrumentService } from '../../../../services/instrument.service';
import { Instrument } from '../../../../models/api.models';
import { PortfolioSummary } from '../../models/portfolio-summary.model';
import { PerformancePeriod, PortfolioPerformance } from '../../models/portfolio-performance.model';

type HistoricalPeriod = '1M' | '3M' | '6M' | '1Y' | '2Y' | 'MAX';

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [CurrencyPipe, DecimalPipe, ReactiveFormsModule, PriceChartComponent, ComparisonChartComponent, PerformancePanelComponent],
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
  private comparisonRequest?: Subscription;
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
  readonly comparison = signal<HistoricalPrices[]>([]);
  readonly comparisonLoading = signal(false);
  readonly comparisonError = signal<string | null>(null);
  readonly comparisonSymbols = signal<string[]>([]);
  readonly comparisonPickerSymbol = signal('');
  readonly historicalPeriods: HistoricalPeriod[] = ['1M', '3M', '6M', '1Y', '2Y', 'MAX'];
  readonly historicalPeriod = signal<HistoricalPeriod>('1Y');
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
  readonly priceStats = computed(() => {
    const prices = this.history()?.prices ?? [];
    if (prices.length === 0) return null;
    const closes = prices.map(point => point.close);
    const latest = closes[closes.length - 1];
    const first = closes[0];
    const logReturns = closes.slice(1).map((close, index) => Math.log(close / closes[index]));
    const average = closes.reduce((sum, close) => sum + close, 0) / closes.length;
    const meanReturn = logReturns.length ? logReturns.reduce((sum, value) => sum + value, 0) / logReturns.length : 0;
    const variance = logReturns.length > 1
      ? logReturns.reduce((sum, value) => sum + (value - meanReturn) ** 2, 0) / (logReturns.length - 1)
      : 0;
    return {
      latest,
      change: latest - first,
      returnPercent: first ? (latest / first - 1) * 100 : 0,
      high: Math.max(...closes),
      low: Math.min(...closes),
      average,
      volatility: Math.sqrt(variance) * Math.sqrt(252) * 100,
      observations: prices.length,
      volume: prices.reduce((sum, point) => sum + point.volume, 0),
      returnObservations: logReturns.length,
    };
  });
  readonly comparisonRows = computed(() => this.comparison().map(history => {
    const first = history.prices[0]?.close ?? 0;
    const latest = history.prices[history.prices.length - 1]?.close ?? 0;
    return { symbol: history.symbol, first, latest, change: latest - first, returnPercent: first ? (latest / first - 1) * 100 : 0, observations: history.prices.length };
  }));

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
      this.loadComparison();
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
        const eligible = instruments.filter(instrument => instrument.currency === 'USD'
          && instrument.assetClass !== 'CASH').sort((a, b) => a.symbol.localeCompare(b.symbol));
        this.instruments.set(eligible);
        this.instrumentsLoading.set(false);
        const current = this.dateForm.controls.symbol.value;
        this.dateForm.controls.symbol.setValue(eligible.find(instrument => instrument.symbol === current)?.symbol
          ?? eligible[0]?.symbol ?? '');
        this.comparisonSymbols.set(this.dateForm.controls.symbol.value ? [this.dateForm.controls.symbol.value] : []);
        this.comparisonPickerSymbol.set(eligible.find(instrument => instrument.symbol !== this.dateForm.controls.symbol.value)?.symbol
          ?? this.dateForm.controls.symbol.value ?? '');
        this.loadComparison();
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
    this.loadComparison();
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

  loadComparison(): void {
    const { from, to } = this.dateForm.getRawValue();
    const symbols = this.comparisonSymbols();
    if (!from || !to || symbols.length === 0) {
      this.comparison.set([]);
      return;
    }
    this.comparisonRequest?.unsubscribe();
    this.comparisonLoading.set(true);
    this.comparisonError.set(null);
    this.comparisonRequest = forkJoin(symbols.map(symbol => this.api.getPrices(symbol, from, to))).pipe(
      timeout(15000), takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: histories => { this.comparison.set(histories.filter(history => history.prices.length > 0)); this.comparisonLoading.set(false); },
      error: (error: Error) => { this.comparison.set([]); this.comparisonLoading.set(false); this.comparisonError.set(error.name === 'TimeoutError' ? 'The comparison data took too long to load.' : error.message); },
    });
  }

  toggleComparisonSymbol(symbol: string, checked: boolean): void {
    const selected = new Set(this.comparisonSymbols());
    if (checked) selected.add(symbol);
    else selected.delete(symbol);
    this.comparisonSymbols.set([...selected]);
    this.loadComparison();
  }

  addComparisonSymbol(): void {
    const symbol = this.comparisonPickerSymbol();
    if (!symbol || this.comparisonSymbols().includes(symbol)) return;
    this.comparisonSymbols.set([...this.comparisonSymbols(), symbol]);
    this.loadComparison();
  }

  removeComparisonSymbol(symbol: string): void {
    this.comparisonSymbols.set(this.comparisonSymbols().filter(selected => selected !== symbol));
    this.loadComparison();
  }

  applyHistoricalPeriod(period: HistoricalPeriod): void {
    this.historicalPeriod.set(period);
    if (period === 'MAX') {
      this.dateForm.controls.from.setValue('1900-01-01', { emitEvent: false });
      this.dateForm.controls.to.setValue(new Date().toISOString().slice(0, 10), { emitEvent: false });
    } else {
      const end = new Date();
      end.setUTCDate(end.getUTCDate() - 1);
      const start = new Date(end);
      start.setUTCMonth(start.getUTCMonth() - Number(period.slice(0, -1)));
      this.dateForm.controls.from.setValue(start.toISOString().slice(0, 10), { emitEvent: false });
      this.dateForm.controls.to.setValue(end.toISOString().slice(0, 10), { emitEvent: false });
    }
    this.loadPrices();
  }

  isComparisonSymbolSelected(symbol: string): boolean {
    return this.comparisonSymbols().includes(symbol);
  }
}
