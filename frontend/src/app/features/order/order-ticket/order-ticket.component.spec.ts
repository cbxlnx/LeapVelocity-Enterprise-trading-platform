import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { QuoteSnapshot } from '../../../models/api.models';
import { AccountService } from '../../../services/account.service';
import { AuthService } from '../../../services/auth.service';
import { InstrumentService } from '../../../services/instrument.service';
import { MarketDataService } from '../../../services/market-data.service';
import { OrderService } from '../../../services/order.service';
import { OrderTicketComponent } from './order-ticket.component';

const aaplQuote: QuoteSnapshot = {
  symbol: 'AAPL',
  lastPrice: 187.5,
  bid: 187.4,
  ask: 187.6,
  currency: 'USD',
  asOf: '2024-01-02T15:30:00Z',
  source: 'TEST',
};

const msftQuote: QuoteSnapshot = {
  symbol: 'MSFT',
  lastPrice: 400,
  bid: null,
  ask: null,
  currency: 'USD',
  asOf: '2024-01-02T15:30:00Z',
  source: 'TEST',
};

describe('OrderTicketComponent quote hydration', () => {
  let fixture: ComponentFixture<OrderTicketComponent>;
  let component: OrderTicketComponent;
  let accountService: {
    getAccount: Mock;
    getBalance: Mock;
    getPositions: Mock;
    getOrders: Mock;
    clearCache: Mock;
  };
  let marketData: { getQuote: Mock };

  beforeEach(() => {
    accountService = {
      getAccount: vi.fn().mockReturnValue(
        of({ id: 1, accountId: 'ACC-1', holderName: 'Alice', cashBalance: 5000, status: 'ACTIVE', version: 1 })
      ),
      getBalance: vi.fn().mockReturnValue(of({ accountId: 1, cashBalance: 5000 })),
      getPositions: vi.fn().mockReturnValue(of([])),
      getOrders: vi.fn().mockReturnValue(of([])),
      clearCache: vi.fn(),
    };
    marketData = { getQuote: vi.fn().mockReturnValue(of(aaplQuote)) };

    TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { getUsername: () => 'alice', getAccountId: () => 1 } },
        { provide: AccountService, useValue: accountService },
        { provide: OrderService, useValue: {} },
        { provide: InstrumentService, useValue: { getInstruments: () => of([]) } },
        { provide: MarketDataService, useValue: marketData },
      ],
    });

    fixture = TestBed.createComponent(OrderTicketComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('prefills the price from the quote when an instrument is chosen', () => {
    component.chooseInstrument('AAPL');

    expect(marketData.getQuote).toHaveBeenCalledWith('AAPL');
    expect(component.orderForm.get('symbol')?.value).toBe('AAPL');
    expect(component.orderForm.get('price')?.value).toBe(187.6);
    expect(component.executableReferencePrice).toBe(187.6);
    expect(component.referenceQuote).toEqual(aaplQuote);
    expect(component.quoteLoading).toBe(false);
    expect(component.quoteError).toBeNull();
  });

  it('normalizes the typed symbol on blur and loads its quote', () => {
    marketData.getQuote.mockReturnValueOnce(of(msftQuote));
    component.orderForm.patchValue({ symbol: '  msft ' });

    component.normalizeSymbol();

    expect(component.orderForm.get('symbol')?.value).toBe('MSFT');
    expect(marketData.getQuote).toHaveBeenCalledWith('MSFT');
    expect(component.orderForm.get('price')?.value).toBeNull();
    expect(component.executableReferencePrice).toBeNull();
  });

  it('uses the bid as the executable reference for SELL orders', () => {
    component.chooseInstrument('AAPL');

    component.orderForm.patchValue({ side: 'SELL' });
    component.onSideChange();

    expect(component.executableReferencePrice).toBe(187.4);
    expect(component.orderForm.get('price')?.value).toBe(187.4);
  });

  it('does not replace a manually edited price when the side changes', () => {
    component.chooseInstrument('AAPL');
    component.orderForm.patchValue({ price: 185 });

    component.orderForm.patchValue({ side: 'SELL' });
    component.onSideChange();

    expect(component.orderForm.get('price')?.value).toBe(185);
  });

  it('ignores a quote response that arrives after the symbol changed', () => {
    const aaplResponse = new Subject<QuoteSnapshot>();
    const msftResponse = new Subject<QuoteSnapshot>();
    marketData.getQuote.mockReturnValueOnce(aaplResponse).mockReturnValueOnce(msftResponse);

    component.chooseInstrument('AAPL');
    expect(component.quoteLoading).toBe(true);

    component.chooseInstrument('MSFT');
    msftResponse.next(msftQuote);
    aaplResponse.next(aaplQuote);

    expect(component.orderForm.get('symbol')?.value).toBe('MSFT');
    expect(component.referenceQuote).toEqual(msftQuote);
    expect(component.orderForm.get('price')?.value).toBeNull();
  });

  it('shows an unavailable state and leaves the price empty when the quote fails', () => {
    marketData.getQuote.mockReturnValueOnce(
      throwError(() => new Error('Unable to load the market quote. The server returned an error (404).'))
    );

    component.chooseInstrument('ZZZZ');

    expect(component.referenceQuote).toBeNull();
    expect(component.quoteLoading).toBe(false);
    expect(component.quoteError).toContain('Unable to load the market quote');
    expect(component.orderForm.get('price')?.value).toBeNull();
  });

  it('does not request a quote for an empty symbol', () => {
    component.chooseInstrument('');

    expect(marketData.getQuote).not.toHaveBeenCalled();
    expect(component.quoteLoading).toBe(false);
  });

  it('clears the reference quote when starting a new order', () => {
    component.chooseInstrument('AAPL');

    component.startNewOrder();

    expect(component.referenceQuote).toBeNull();
    expect(component.quoteError).toBeNull();
    expect(component.quoteLoading).toBe(false);
  });
});
