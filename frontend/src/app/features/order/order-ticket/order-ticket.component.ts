import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
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
import { OrderResponse } from '../../../models/api.models';

@Component({
  selector: 'app-order-ticket',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
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
  cashBalance = 0;

  readonly instruments = [
    { symbol: 'GLBEQ1', name: 'Global Equity Index Fund', exchange: 'FUND', currency: 'GBP', marketPrice: 100 },
    { symbol: 'CORPB1', name: 'Sterling Corporate Bond Fund', exchange: 'FUND', currency: 'GBP', marketPrice: 98.5 },
    { symbol: 'GILT10', name: 'UK 10-Year Gilt', exchange: 'FUND', currency: 'GBP', marketPrice: 101.25 },
    { symbol: 'USEQUITY', name: 'US Equity Fund', exchange: 'FUND', currency: 'GBP', marketPrice: 150 },
    { symbol: 'EMMARKET', name: 'Emerging Markets Fund', exchange: 'FUND', currency: 'GBP', marketPrice: 87.5 },
    { symbol: 'EUROFIX', name: 'Euro Fixed Income Fund', exchange: 'FUND', currency: 'GBP', marketPrice: 102 },
    { symbol: 'MULTIFUND', name: 'Multi-Asset Fund', exchange: 'FUND', currency: 'GBP', marketPrice: 110 },
  ];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private accountService: AccountService,
    private orderService: OrderService,
    private changeDetector: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.userName = this.authService.getUsername() ?? 'User';
    this.accountId = this.authService.getAccountId();
    this.orderForm = this.fb.group({
      symbol: ['GLBEQ1', Validators.required],
      side: ['BUY', Validators.required],
      quantity: [100, [Validators.required, Validators.min(1)]],
      price: [192, [Validators.required, Validators.min(0.01)]],
      duration: ['DAY', Validators.required],
    });

    if (this.accountId) {
      this.accountService.getBalance(this.accountId).subscribe({
        next: (balance) => {
          this.cashBalance = balance.cashBalance;
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
      exchange: 'Exchange pending',
      currency: 'USD',
      marketPrice: Number(this.orderForm.get('price')?.value) || 0,
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

  get displayAccountId(): string {
    return this.accountId === null
      ? 'ACC---'
      : `ACC-${String(this.accountId).padStart(3, '0')}`;
  }

  onSymbolChange(): void {
    this.showInstrumentOptions = true;
    const symbol = String(this.orderForm.get('symbol')?.value ?? '').trim().toUpperCase();
    const instrument = this.instruments.find((item) => item.symbol === symbol);

    if (instrument) {
      this.orderForm.patchValue({ price: instrument.marketPrice }, { emitEvent: false });
    }
  }

  normalizeSymbol(): void {
    const symbol = String(this.orderForm.get('symbol')?.value ?? '').trim().toUpperCase();
    this.orderForm.patchValue({ symbol }, { emitEvent: false });
    window.setTimeout(() => {
      this.showInstrumentOptions = false;
    }, 150);
  }

  chooseInstrument(symbol: string): void {
    const instrument = this.instruments.find((item) => item.symbol === symbol);
    this.orderForm.patchValue(
      { symbol, price: instrument?.marketPrice ?? this.orderForm.get('price')?.value },
      { emitEvent: false }
    );
    this.showInstrumentOptions = false;
  }

  submitOrder(): void {
    if (this.orderForm.invalid || this.isSubmitting) {
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
        this.changeDetector.detectChanges();
      },
      error: (error) => {
        this.orderError = error?.message || 'The order was rejected. Please review and try again.';
        this.isSubmitting = false;
        this.changeDetector.detectChanges();
      },
    });
  }

  private createIdempotencyKey(): string {
    return typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}
