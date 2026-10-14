import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, timeout } from 'rxjs';
import { PriceChartComponent } from '../../components/price-chart/price-chart.component';
import { HistoricalPrices } from '../../models/historical-prices.model';
import { defaultHistoryRange, formatTradingDate } from '../../models/trading-date';
import { AnalyticsApiService } from '../../services/analytics-api.service';
import { AuthService } from '../../../../services/auth.service';
import { PortfolioSummary } from '../../models/portfolio-summary.model';
import { PortfolioSummaryComponent } from '../../components/portfolio-summary/portfolio-summary.component';

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [CurrencyPipe, ReactiveFormsModule, PriceChartComponent, PortfolioSummaryComponent],
  templateUrl: './analytics-dashboard.component.html',
  styleUrl: './analytics-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsDashboardComponent implements OnInit {
  private readonly api = inject(AnalyticsApiService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  private summaryRequest?: Subscription;
  private readonly initialRange = defaultHistoryRange();
  readonly formatDate = formatTradingDate;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly history = signal<HistoricalPrices | null>(null);
  readonly summary = signal<PortfolioSummary | null>(null);
  readonly summaryLoading = signal(false);
  readonly summaryError = signal<string | null>(null);
  readonly dateForm = new FormGroup({
    from: new FormControl(this.initialRange.from, { nonNullable: true, validators: [Validators.required] }),
    to: new FormControl(this.initialRange.to, { nonNullable: true, validators: [Validators.required] }),
  }, { validators: (form) => {
    const { from, to } = form.value;
    return from && to && from > to ? { dateOrder: true } : null;
  } });

  ngOnInit(): void {
    this.loadSummary();
    this.loadPrices();
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
    if (this.dateForm.invalid) {
      this.dateForm.markAllAsTouched();
      return;
    }
    this.request?.unsubscribe();
    this.history.set(null);
    this.error.set(null);
    this.loading.set(true);
    const { from, to } = this.dateForm.getRawValue();
    this.request = this.api.getPrices('AAPL', from, to).pipe(
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
