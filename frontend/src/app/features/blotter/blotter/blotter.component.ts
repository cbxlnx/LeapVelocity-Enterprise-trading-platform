import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, forkJoin, finalize, takeUntil, timeout } from 'rxjs';
import { AccountService } from '../../../services/account.service';
import { AuthService } from '../../../services/auth.service';
import { Account, Order, OrderSide, OrderStatus } from '../../../models/api.models';

@Component({
  selector: 'app-blotter',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './blotter.component.html',
  styleUrl: './blotter.component.css',
})
export class BlotterComponent implements OnInit, OnDestroy {
  account: Account | null = null;
  orders: Order[] = [];
  isLoading = true;
  error: string | null = null;
  selectedSymbol = '';
  selectedSide = '';
  selectedStatus = '';
  selectedDateRange = 'all';
  page = 1;
  readonly pageSize = 8;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly accountService: AccountService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly changeDetector: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    setTimeout(() => this.loadOrders(), 0);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadOrders(): void {
    const accountId = this.authService.getAccountId();
    if (!accountId) {
      this.error = 'Please sign in before loading order history.';
      this.isLoading = false;
      return;
    }

    this.isLoading = true;
    this.error = null;
    this.accountService.clearCache();

    forkJoin({
      account: this.accountService.getAccount(accountId),
      orders: this.accountService.getOrders(accountId),
    }).pipe(
      timeout(10000),
      takeUntil(this.destroy$),
      finalize(() => {
        this.isLoading = false;
        this.changeDetector.detectChanges();
      })
    ).subscribe({
      next: ({ account, orders }) => {
        this.account = account;
        this.orders = orders;
        this.page = 1;
      },
      error: (error) => {
        this.error = error?.message || 'Unable to load order history.';
      },
    });
  }

  get symbols(): string[] {
    return [...new Set(this.orders.map((order) => order.symbol))].sort();
  }

  get filteredOrders(): Order[] {
    const now = Date.now();
    const rangeDays = this.selectedDateRange === '7' ? 7 : this.selectedDateRange === '30' ? 30 : null;

    return this.orders.filter((order) => {
      const matchesSymbol = !this.selectedSymbol || order.symbol === this.selectedSymbol;
      const matchesSide = !this.selectedSide || order.side === this.selectedSide;
      const matchesStatus = !this.selectedStatus || order.status === this.selectedStatus;
      const matchesDate = !rangeDays || now - new Date(order.createdOn).getTime() <= rangeDays * 86400000;
      return matchesSymbol && matchesSide && matchesStatus && matchesDate;
    });
  }

  get visibleOrders(): Order[] {
    const start = (this.page - 1) * this.pageSize;
    return this.filteredOrders.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredOrders.length / this.pageSize));
  }

  get firstVisibleOrder(): number {
    return this.filteredOrders.length ? (this.page - 1) * this.pageSize + 1 : 0;
  }

  get lastVisibleOrder(): number {
    return Math.min(this.page * this.pageSize, this.filteredOrders.length);
  }

  get newCount(): number {
    return this.filteredOrders.filter((order) => order.status === 'NEW').length;
  }

  get filledCount(): number {
    return this.filteredOrders.filter((order) => order.status === 'FILLED').length;
  }

  setFilter(): void {
    this.page = 1;
  }

  clearFilters(): void {
    this.selectedSymbol = '';
    this.selectedSide = '';
    this.selectedStatus = '';
    this.selectedDateRange = 'all';
    this.page = 1;
  }

  previousPage(): void {
    this.page = Math.max(1, this.page - 1);
  }

  nextPage(): void {
    this.page = Math.min(this.totalPages, this.page + 1);
  }

  placeOrder(): void {
    this.router.navigate(['/order']);
  }

  formatAccountId(): string {
    return this.account ? `ACC-${this.account.id.toString().padStart(3, '0')}` : 'ACC---';
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
    }).format(new Date(value));
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency', currency: 'USD', minimumFractionDigits: 2,
    }).format(value);
  }

  getSideClass(side: OrderSide): string {
    return side === 'BUY' ? 'buy' : 'sell';
  }

  getStatusClass(status: OrderStatus): string {
    return `status-${status.toLowerCase()}`;
  }
}
