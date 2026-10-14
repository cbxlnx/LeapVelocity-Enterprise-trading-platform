import { ChangeDetectorRef, Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin, Subject, Subscription, timeout, finalize } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AccountService } from '../../../services/account.service';
import { AuthService } from '../../../services/auth.service';
import { Account as ApiAccount, Position as ApiPosition } from '../../../models/api.models';
import { DashboardData } from '../../../shared/models/account.model';

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
  isLoading = true;
  error: string | null = null;

  displayedColumns: string[] = ['symbol', 'quantity', 'averageCost', 'currentMarketValue', 'gainLoss'];
  private destroy$ = new Subject<void>();
  private dashboardRequest?: Subscription;
  private loadingWatchdog?: ReturnType<typeof setTimeout>;

  constructor(
    private accountService: AccountService,
    private authService: AuthService,
    private router: Router,
    private changeDetector: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    setTimeout(() => this.loadDashboardData(), 0);
  }

  ngOnDestroy(): void {
    this.dashboardRequest?.unsubscribe();
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
    })
      .pipe(
        timeout(10000),
        takeUntil(this.destroy$),
        finalize(() => {
          this.isLoading = false;
        })
      )
      .subscribe({
        next: ({ account, balance, positions }) => {
          try {
            this.dashboardData = this.buildDashboardData(account, balance, positions);
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
  }

  private buildDashboardData(
    account: ApiAccount,
    balance: { accountId: number; cashBalance: number },
    positions: ApiPosition[]
  ): DashboardData {
    const mappedPositions = positions.map((position, index) => {
      const costBasis = position.quantity * position.averageCost;
      const marketValue = position.marketValue ?? costBasis;
      const gainLoss = marketValue - costBasis;

      return {
        id: index,
        accountId: position.accountId,
        symbol: position.symbol,
        quantity: position.quantity,
        averageCost: position.averageCost,
        currentMarketValue: marketValue,
        gainLoss,
        gainLossPercent: costBasis === 0 ? 0 : (gainLoss / costBasis) * 100,
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

    return {
      account: {
        ...account,
        lastUpdated: new Date(),
      },
      balance,
      positions: mappedPositions,
      totalMarketValue,
      totalEquity: balance.cashBalance + totalMarketValue,
      unrealizedGainLoss,
      unrealizedGainLossPercent:
        totalMarketValue - unrealizedGainLoss === 0
          ? 0
          : (unrealizedGainLoss / (totalMarketValue - unrealizedGainLoss)) * 100,
    };
  }

  navigateToOrder(): void {
    this.router.navigate(['/order']);
  }

  navigateToBlotter(): void {
    this.router.navigate(['/blotter']);
  }

  logout(): void {
    this.router.navigate(['/login']);
  }

  getStatusBadgeClass(status: string): string {
    return `status-badge status-${status.toLowerCase()}`;
  }

  getGainLossClass(gainLoss: number | undefined): string {
    return gainLoss !== undefined && gainLoss < 0 ? 'loss' : 'gain';
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

  getPortfolioCostBasis(data: DashboardData): number {
    return data.totalMarketValue - data.unrealizedGainLoss;
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
}