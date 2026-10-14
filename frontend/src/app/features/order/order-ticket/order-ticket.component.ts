import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ChangeDetectorRef } from '@angular/core';
import { AuthService } from '../../../services/auth.service';
import { AccountService } from '../../../services/account.service';
import { OrderService } from '../../../services/order.service';
import { AccountStatus, Instrument, Order, OrderResponse, Position } from '../../../models/api.models';
import { InstrumentService } from '../../../services/instrument.service';

@Component({
  selector: 'app-order-ticket',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './order-ticket.component.html',
  styleUrl: './order-ticket.component.css',
})
export class OrderTicketComponent implements OnInit {
  orderForm!: FormGroup;
  orderSubmitted = false;
  isSubmitting = false;
  orderError: string | null = null;
  orderResponse: OrderResponse | null = null;
  showInstrumentOptions = false;
  userName = 'User';
  accountId: number | null = null;
  accountStatus: AccountStatus = 'LOADING';
  cashBalance = 0;
  positions: Position[] = [];
  instruments: Instrument[] = [];
  instrumentError: string | null = null;
  recentOrders: Order[] = [];
  recentOrdersError: string | null = null;
  cancellingOrderId: string | null = null;
  orderPendingCancellation: Order | null = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private accountService: AccountService,
    private orderService: OrderService,
    private instrumentService: InstrumentService,
    private changeDetector: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.userName = this.authService.getUsername() ?? 'User';
    this.accountId = this.authService.getAccountId();
    this.orderForm = this.fb.group({
      symbol: ['', Validators.required],
      side: ['BUY', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1)]],
      price: [null, [Validators.required, Validators.min(0.01)]],
      duration: ['DAY', Validators.required],
    });

    this.instrumentService.getInstruments().subscribe({
      next: (instruments) => {
        this.instruments = instruments;
        this.changeDetector.detectChanges();
      },
      error: (error) => {
        this.instrumentError = error?.message || 'Unable to load instruments.';
        this.changeDetector.detectChanges();
      },
    });

    if (this.accountId) {
      this.loadRecentOrders(this.accountId);
      this.accountService.getAccount(this.accountId).subscribe({
        next: (account) => {
          this.accountStatus = account.status;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.accountStatus = 'UNAVAILABLE';
          this.changeDetector.detectChanges();
        },
      });
      this.accountService.getBalance(this.accountId).subscribe({
        next: (balance) => {
          this.cashBalance = balance.cashBalance;
          this.changeDetector.detectChanges();
        },
      });
      this.accountService.getPositions(this.accountId).subscribe({
        next: (positions) => {
          this.positions = positions;
          this.changeDetector.detectChanges();
        },
      });
    }
  }

  get selectedInstrument() {
    const symbol = String(this.orderForm.get('symbol')?.value ?? '').trim().toUpperCase();
    return this.instruments.find((instrument) => instrument.symbol === symbol) ?? {
      symbol: symbol || 'TICKER',
      name: 'Instrument lookup pending',
      assetClass: 'Asset class pending',
      currency: 'USD',
      id: 0,
      tradable: false,
    };
  }

  get filteredInstruments() {
    const query = String(this.orderForm.get('symbol')?.value ?? '').trim().toUpperCase();
    return this.instruments.filter(
      (instrument) =>
        instrument.symbol.includes(query) || instrument.name.toUpperCase().includes(query)
    );
  }

  get estimatedValue(): number {
    const quantity = Number(this.orderForm.get('quantity')?.value) || 0;
    const price = Number(this.orderForm.get('price')?.value) || 0;
    return quantity * price;
  }

  get estimatedCashAfterOrder(): number {
    return this.orderForm.get('side')?.value === 'BUY'
      ? this.cashBalance - this.estimatedValue
      : this.cashBalance + this.estimatedValue;
  }

  get isFormReady(): boolean {
    return this.orderForm.valid;
  }

  get orderBusinessError(): string | null {
    const quantity = Number(this.orderForm.get('quantity')?.value);
    const price = Number(this.orderForm.get('price')?.value);
    const side = this.orderForm.get('side')?.value;

    if (!Number.isFinite(quantity) || !Number.isFinite(price) || quantity <= 0 || price <= 0) {
      return null;
    }

    if (side === 'BUY' && this.estimatedValue > this.cashBalance) {
      return `Insufficient cash. This order requires ${this.formatMoney(this.estimatedValue)}, but only ${this.formatMoney(this.cashBalance)} is available.`;
    }

    if (side === 'SELL' && quantity > this.heldQuantity) {
      return `Insufficient holdings. You are trying to sell ${quantity} shares, but only ${this.heldQuantity} are available.`;
    }

    return null;
  }

  get hasOrderBusinessError(): boolean {
    return this.orderBusinessError !== null;
  }

  get heldQuantity(): number {
    const symbol = String(this.orderForm.get('symbol')?.value ?? '').trim().toUpperCase();
    return this.positions.find((position) => position.symbol.toUpperCase() === symbol)?.quantity ?? 0;
  }

  get maxAffordableQuantity(): number {
    const price = Number(this.orderForm.get('price')?.value) || 0;
    return price > 0 ? Math.floor(this.cashBalance / price) : 0;
  }

  get quantityGuidance(): string {
    return this.orderForm.get('side')?.value === 'SELL'
      ? `Available to sell: ${this.heldQuantity} shares`
      : `Maximum affordable: ${this.maxAffordableQuantity} shares`;
  }

  get displayAccountId(): string {
    return this.accountId === null
      ? 'ACC---'
      : `ACC-${String(this.accountId).padStart(3, '0')}`;
  }

  onSymbolChange(): void {
    this.showInstrumentOptions = true;
  }

  normalizeSymbol(): void {
    const symbol = String(this.orderForm.get('symbol')?.value ?? '').trim().toUpperCase();
    this.orderForm.patchValue({ symbol }, { emitEvent: false });
    window.setTimeout(() => {
      this.showInstrumentOptions = false;
    }, 150);
  }

  chooseInstrument(symbol: string): void {
    this.orderForm.patchValue(
      { symbol },
      { emitEvent: false }
    );
    this.showInstrumentOptions = false;
  }

  submitOrder(): void {
    if (this.orderForm.invalid || this.hasOrderBusinessError || this.isSubmitting) {
      this.orderForm.markAllAsTouched();
      return;
    }

    const accountId = this.authService.getAccountId();
    if (!accountId) {
      this.orderError = 'Please sign in before placing an order.';
      return;
    }

    const { symbol, side, quantity, price } = this.orderForm.getRawValue();
    this.isSubmitting = true;
    this.orderSubmitted = false;
    this.orderError = null;
    this.orderResponse = null;

    this.orderService.placeOrder({
      accountId,
      symbol: String(symbol).trim().toUpperCase(),
      side,
      quantity: Number(quantity),
      price: Number(price),
      idempotencyKey: this.createIdempotencyKey(),
    }).subscribe({
      next: (response) => {
        this.orderResponse = response;
        this.orderSubmitted = true;
        this.isSubmitting = false;
        this.accountService.clearCache();
        this.loadRecentOrders(accountId);
        this.changeDetector.detectChanges();
      },
      error: (error) => {
        this.orderError = error?.message || 'The order was rejected. Please review and try again.';
        this.isSubmitting = false;
        this.changeDetector.detectChanges();
      },
    });
  }

  startNewOrder(): void {
    this.orderSubmitted = false;
    this.orderResponse = null;
    this.orderError = null;
    this.orderForm.reset({
      symbol: '',
      side: 'BUY',
      quantity: 100,
      price: null,
      duration: 'DAY',
    });
  }

  cancelOrder(order: Order): void {
    if (order.status !== 'NEW' || this.cancellingOrderId) {
      return;
    }

    this.orderPendingCancellation = order;
  }

  dismissCancellation(): void {
    if (!this.cancellingOrderId) {
      this.orderPendingCancellation = null;
    }
  }

  confirmCancellation(): void {
    const order = this.orderPendingCancellation;
    if (!order || order.status !== 'NEW' || this.cancellingOrderId) {
      return;
    }

    this.cancellingOrderId = order.id;
    this.orderPendingCancellation = null;
    this.recentOrdersError = null;
    this.orderService.cancelOrder(order.id).subscribe({
      next: () => {
        this.cancellingOrderId = null;
        this.accountService.clearCache();
        if (this.accountId) {
          this.loadRecentOrders(this.accountId);
          this.loadAccountData(this.accountId);
        }
        this.changeDetector.detectChanges();
      },
      error: (error) => {
        this.cancellingOrderId = null;
        const message = String(error?.message || '');
        const orderWasAlreadyProcessed = message.includes('Cannot cancel order with status:');

        if (this.accountId) {
          this.accountService.clearCache();
          this.loadRecentOrders(this.accountId, false);
          this.loadAccountData(this.accountId);
        }

        this.recentOrdersError = orderWasAlreadyProcessed
          ? 'This order was already processed before cancellation completed. The order history has been refreshed.'
          : message || 'Unable to cancel the order.';
        this.changeDetector.detectChanges();
      },
    });
  }

  private loadRecentOrders(accountId: number, clearError = true): void {
    if (clearError) {
      this.recentOrdersError = null;
    }
    this.accountService.getOrders(accountId).subscribe({
      next: (orders) => {
        this.recentOrders = [...orders]
          .sort((first, second) =>
            new Date(second.createdOn).getTime() - new Date(first.createdOn).getTime()
          )
          .slice(0, 5);
        this.changeDetector.detectChanges();
      },
      error: (error) => {
        this.recentOrdersError = error?.message || 'Unable to load recent orders.';
        this.changeDetector.detectChanges();
      },
    });
  }

  private loadAccountData(accountId: number): void {
    this.accountService.getBalance(accountId).subscribe({
      next: (balance) => {
        this.cashBalance = balance.cashBalance;
        this.changeDetector.detectChanges();
      },
    });
    this.accountService.getPositions(accountId).subscribe({
      next: (positions) => {
        this.positions = positions;
        this.changeDetector.detectChanges();
      },
    });
  }

  private createIdempotencyKey(): string {
    return typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  private formatMoney(amount: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }
}
