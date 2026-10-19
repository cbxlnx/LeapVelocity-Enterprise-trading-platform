import { ChangeDetectorRef, Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin, map, Subject, Subscription, switchMap, timeout, finalize } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AccountService } from '../../../services/account.service';
import { AuthService } from '../../../services/auth.service';
import { Account as ApiAccount, Instrument, Position as ApiPosition, QuoteSnapshot } from '../../../models/api.models';
import { DashboardData, Position } from '../../../shared/models/account.model';
import { MarketDataService, normalizeSymbol } from '../../../services/market-data.service';
import { InstrumentService } from '../../../services/instrument.service';

interface MarketOverviewRow {
  instrument: Instrument;
  quote: QuoteSnapshot | null;
  priceDirection: 'up' | 'down' | 'unchanged';
}

interface MarketOverviewData {
  rows: MarketOverviewRow[];
  totalTracked: number;
  liveQuotes: number;
  awaitingQuotes: number;
  lastUpdated: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatTableModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit, OnDestroy {
  dashboardData: DashboardData | null = null;
  marketOverview: MarketOverviewData | null = null;
  isLoading = true;
  isMarketOverviewLoading = true;
  error: string | null = null;
  marketOverviewError: string | null = null;

  displayedColumns: string[] = ['symbol', 'quantity', 'averageCost', 'currentMarketValue', 'gainLoss'];
  private destroy$ = new Subject<void>();
  private dashboardRequest?: Subscription;
  private marketOverviewRequest?: Subscription;
  private marketRefreshTimer?: ReturnType<typeof setInterval>;
  private loadingWatchdog?: ReturnType<typeof setTimeout>;

  constructor(
    private accountService: AccountService,
    private authService: AuthService,
    private marketDataService: MarketDataService,
    private instrumentService: InstrumentService,
    private router: Router,
    private changeDetector: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    setTimeout(() => this.loadDashboardData(), 0);
    this.loadMarketOverview();
    this.marketRefreshTimer = setInterval(() => {
      this.refreshMarketData();
    }, 10000);
  }

  ngOnDestroy(): void {
    this.dashboardRequest?.unsubscribe();
    this.marketOverviewRequest?.unsubscribe();
    if (this.marketRefreshTimer) {
      clearInterval(this.marketRefreshTimer);
      this.marketRefreshTimer = undefined;
    }
    this.clearLoadingWatchdog();
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadDashboardData(): void {
    this.dashboardRequest?.unsubscribe();
    this.clearLoadingWatchdog();
    this.isLoading = true;
    this.error = null;

    this.loadingWatchdog = setTimeout(() => {
      if (!this.isLoading) {
        return;
      }

      this.dashboardRequest?.unsubscribe();
      this.isLoading = false;
      this.error = 'The dashboard service did not respond. Please retry.';
      this.changeDetector.detectChanges();
    }, 12000);

    const accountId = this.authService.getAccountId();
    if (!accountId) {
      this.clearLoadingWatchdog();
      this.error = 'Please sign in before loading your trading account.';
      this.isLoading = false;
      return;
    }

    this.dashboardRequest = forkJoin({
      account: this.accountService.getAccount(accountId),
      balance: this.accountService.getBalance(accountId),
      positions: this.accountService.getPositions(accountId),
      instruments: this.instrumentService.getInstruments(),
    })
      .pipe(
        switchMap(({ account, balance, positions, instruments }) =>
          this.marketDataService.getBatchQuotes(positions.map((position) => position.symbol)).pipe(
            map((batch) => ({ account, balance, positions, instruments, quotes: batch.quotes }))
          )
        ),
        timeout(10000),
        takeUntil(this.destroy$),
        finalize(() => {
          this.isLoading = false;
        })
      )
      .subscribe({
        next: ({ account, balance, positions, instruments, quotes }) => {
          try {
            this.dashboardData = this.buildDashboardData(account, balance, positions, instruments, quotes);
            this.error = null;
          } catch (error) {
            this.dashboardData = null;
            this.error = 'The dashboard response could not be displayed.';
            console.error('Dashboard mapping error:', error);
          } finally {
            this.clearLoadingWatchdog();
            this.isLoading = false;
            this.changeDetector.detectChanges();
          }
        },
        error: (err) => {
          this.clearLoadingWatchdog();
          this.dashboardData = null;
          this.isLoading = false;
          this.error = err?.name === 'TimeoutError'
            ? 'The account service took too long to respond. Please retry.'
            : 'Failed to load dashboard data. Please try again.';
          console.error('Dashboard error:', err);
          this.changeDetector.detectChanges();
        },
      });
  }

  private clearLoadingWatchdog(): void {
    if (this.loadingWatchdog) {
      clearTimeout(this.loadingWatchdog);
      this.loadingWatchdog = undefined;
    }
  }

  refreshData(): void {
    this.accountService.clearCache();
    this.loadDashboardData();
    this.loadMarketOverview();
  }

  private refreshMarketData(): void {
    if (this.isMarketOverviewLoading || !this.dashboardData) {
      return;
    }

    const positions = this.dashboardData.positions;
    this.marketOverviewRequest?.unsubscribe();
    this.isMarketOverviewLoading = true;
    this.marketOverviewError = null;

    this.marketOverviewRequest = this.instrumentService.getInstruments().pipe(
      map((instruments) => instruments
        .filter((instrument) => instrument.tradable && instrument.currency === 'USD' && instrument.assetClass !== 'CASH')
        .sort((left, right) => left.symbol.localeCompare(right.symbol))),
      switchMap((instruments) => {
        const symbols = [
          ...instruments.map((instrument) => instrument.symbol),
          ...positions.map((position) => position.symbol),
        ];
        return this.marketDataService.getBatchQuotes(symbols).pipe(
          map((batch) => ({ instruments, batch }))
        );
      }),
      timeout(10000),
      takeUntil(this.destroy$),
      finalize(() => {
        this.isMarketOverviewLoading = false;
        this.changeDetector.detectChanges();
      })
    ).subscribe({
      next: ({ instruments, batch }) => {
        this.updateMarketOverview(instruments, batch);
        this.updatePositionData(batch);
        this.changeDetector.detectChanges();
      },
      error: (error) => {
        this.marketOverviewError = error?.name === 'TimeoutError'
          ? 'The market data service took too long to respond. Please retry.'
          : 'Failed to refresh live market data. Please try again.';
        console.error('Market refresh error:', error);
        this.changeDetector.detectChanges();
      },
    });
  }

  loadMarketOverview(showLoading = true): void {
    this.marketOverviewRequest?.unsubscribe();
    if (showLoading) {
      this.isMarketOverviewLoading = true;
    }
    this.marketOverviewError = null;

    this.marketOverviewRequest = this.instrumentService.getInstruments().pipe(
      map((instruments) => instruments
        .filter((instrument) => instrument.tradable && instrument.currency === 'USD' && instrument.assetClass !== 'CASH')
        .sort((left, right) => left.symbol.localeCompare(right.symbol))),
      switchMap((instruments) => this.marketDataService.getBatchQuotes(instruments.map((instrument) => instrument.symbol)).pipe(
        map((batch) => ({ instruments, batch }))
      )),
      timeout(10000),
      takeUntil(this.destroy$),
      finalize(() => {
        this.isMarketOverviewLoading = false;
        this.changeDetector.detectChanges();
      })
    ).subscribe({
      next: ({ instruments, batch }) => {
        this.updateMarketOverview(instruments, batch);
        this.marketOverviewError = null;
        this.changeDetector.detectChanges();
      },
      error: (error) => {
        this.marketOverview = null;
        this.marketOverviewError = error?.name === 'TimeoutError'
          ? 'The market data service took too long to respond. Please retry.'
          : 'Failed to load live market data. Please try again.';
        console.error('Market overview error:', error);
        this.changeDetector.detectChanges();
      },
    });
  }

  private updateMarketOverview(instruments: Instrument[], batch: { quotes: QuoteSnapshot[]; missingSymbols: string[]; serverTimestamp: string }): void {
    const quotes = new Map(batch.quotes.map((quote) => [normalizeSymbol(quote.symbol), quote]));
    const previousQuotes = new Map(
      (this.marketOverview?.rows ?? [])
        .filter((row) => row.quote)
        .map((row) => [normalizeSymbol(row.instrument.symbol), row.quote as QuoteSnapshot])
    );
    const rows = instruments.map((instrument) => ({
      instrument,
      quote: quotes.get(normalizeSymbol(instrument.symbol)) ?? null,
      priceDirection: this.getPriceDirection(
        previousQuotes.get(normalizeSymbol(instrument.symbol)),
        quotes.get(normalizeSymbol(instrument.symbol))
      ),
    }));

    this.marketOverview = {
      rows,
      totalTracked: instruments.length,
      liveQuotes: batch.quotes.length,
      awaitingQuotes: batch.missingSymbols.length,
      lastUpdated: batch.serverTimestamp,
    };
  }

  private updatePositionData(batch: { quotes: QuoteSnapshot[] }): void {
    const positions = this.dashboardData?.positions;
    if (!positions?.length) {
      return;
    }

    const quotes = new Map(batch.quotes.map((quote) => [normalizeSymbol(quote.symbol), quote]));
    const updatedPositions = positions.map((position) => {
            const quote = quotes.get(normalizeSymbol(position.symbol));
            if (!quote) {
              return { ...position, quoteAvailable: false };
            }

            const costBasis = position.quantity * position.averageCost;
            const currentMarketValue = position.quantity * quote.lastPrice;
            const gainLoss = currentMarketValue - costBasis;
            return {
              ...position,
              currentMarketValue,
              gainLoss,
              gainLossPercent: costBasis === 0 ? 0 : (gainLoss / costBasis) * 100,
              quoteAvailable: true,
            };
          });
    const totalMarketValue = updatedPositions.reduce(
            (total, position) => total + (position.currentMarketValue ?? 0),
            0
          );
    const unrealizedGainLoss = updatedPositions.reduce(
            (total, position) => total + (position.gainLoss ?? 0),
            0
          );
    const portfolioCostBasis = updatedPositions
            .filter((position) => position.quoteAvailable)
            .reduce((total, position) => total + position.quantity * position.averageCost, 0);

    if (this.dashboardData) {
      this.dashboardData = {
              ...this.dashboardData,
              positions: updatedPositions,
              totalMarketValue,
              totalEquity: this.dashboardData.balance.cashBalance + totalMarketValue,
              portfolioCostBasis,
              unrealizedGainLoss,
              unrealizedGainLossPercent: portfolioCostBasis === 0
                ? 0
                : (unrealizedGainLoss / portfolioCostBasis) * 100,
              unavailableQuoteCount: updatedPositions.filter((position) => !position.quoteAvailable).length,
            };
    }
  }

  private buildDashboardData(
    account: ApiAccount,
    balance: { accountId: number; cashBalance: number },
    positions: ApiPosition[],
    instruments: Instrument[],
    quotes: QuoteSnapshot[]
  ): DashboardData {
    const instrumentNames = new Map(
      instruments.map((instrument) => [normalizeSymbol(instrument.symbol), instrument.name])
    );
    const mappedPositions: Position[] = positions.map((position, index) => {
      const costBasis = position.quantity * position.averageCost;
      const quote = quotes.find(
        (item) => normalizeSymbol(item.symbol) === normalizeSymbol(position.symbol)
      );
      const quoteAvailable = quote !== undefined;
      const marketValue = quote ? position.quantity * quote.lastPrice : 0;
      const gainLoss = quote ? marketValue - costBasis : 0;

      return {
        id: index,
        accountId: position.accountId,
        symbol: position.symbol,
        instrumentName: instrumentNames.get(normalizeSymbol(position.symbol)),
        quantity: position.quantity,
        averageCost: position.averageCost,
        currentMarketValue: marketValue,
        gainLoss,
        gainLossPercent: quoteAvailable && costBasis !== 0 ? (gainLoss / costBasis) * 100 : 0,
        quoteAvailable,
      };
    });
    const totalMarketValue = mappedPositions.reduce(
      (total, position) => total + (position.currentMarketValue ?? 0),
      0
    );
    const unrealizedGainLoss = mappedPositions.reduce(
      (total, position) => total + (position.gainLoss ?? 0),
      0
    );
    const portfolioCostBasis = mappedPositions
      .filter((position) => position.quoteAvailable)
      .reduce((total, position) => total + position.quantity * position.averageCost, 0);
    const unavailableQuoteCount = mappedPositions.filter(
      (position) => !position.quoteAvailable
    ).length;

    return {
      account: {
        ...account,
        lastUpdated: new Date(),
      },
      balance,
      positions: mappedPositions,
      totalMarketValue,
      totalEquity: balance.cashBalance + totalMarketValue,
      portfolioCostBasis,
      unrealizedGainLoss,
      unrealizedGainLossPercent:
        portfolioCostBasis === 0 ? 0 : (unrealizedGainLoss / portfolioCostBasis) * 100,
      unavailableQuoteCount,
    };
  }

  navigateToOrder(): void {
    this.router.navigate(['/order']);
  }

  navigateToBlotter(): void {
    this.router.navigate(['/blotter']);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  getStatusBadgeClass(status: string): string {
    return `status-badge status-${status.toLowerCase()}`;
  }

  getGainLossClass(gainLoss: number | undefined): string {
    return gainLoss !== undefined && gainLoss < 0 ? 'loss' : 'gain';
  }

  getGainLossDirection(gainLoss: number | undefined): string {
    if (gainLoss === undefined || gainLoss === 0) {
      return '→';
    }
    return gainLoss > 0 ? '↑' : '↓';
  }

  getPriceDirection(previous: QuoteSnapshot | undefined, current: QuoteSnapshot | undefined): 'up' | 'down' | 'unchanged' {
    if (!previous || !current || current.lastPrice === previous.lastPrice) {
      return 'unchanged';
    }
    return current.lastPrice > previous.lastPrice ? 'up' : 'down';
  }

  getPriceDirectionArrow(direction: 'up' | 'down' | 'unchanged'): string {
    return direction === 'up' ? '↑' : direction === 'down' ? '↓' : '→';
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }

  formatPercent(value: number): string {
    return (value >= 0 ? '+' : '') + value.toFixed(2) + '%';
  }

  formatUpdatedAt(value: Date | string): string {
    const date = value instanceof Date ? value : new Date(value);
    const datePart = date.toLocaleDateString('en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC',
    });
    const timePart = date.toLocaleTimeString('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
      timeZone: 'UTC',
    });

    return `Updated ${datePart}, ${timePart} UTC`;
  }

  formatQuoteTime(value: Date | string): string {
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
      return 'Unavailable';
    }

    return date.toLocaleTimeString('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'UTC',
    }) + ' UTC';
  }
}