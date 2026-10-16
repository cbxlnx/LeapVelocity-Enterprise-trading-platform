import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { BatchQuotesResponse, QuoteSnapshot } from '../../../models/api.models';
import { AccountService } from '../../../services/account.service';
import { AuthService } from '../../../services/auth.service';
import { MarketDataService } from '../../../services/market-data.service';
import { InstrumentService } from '../../../services/instrument.service';
import { DashboardComponent } from './dashboard.component';

const account = {
  id: 1,
  accountId: 'ACC-1',
  holderName: 'Alice',
  cashBalance: 400,
  status: 'ACTIVE',
  version: 1,
};

function quote(symbol: string, lastPrice: number): QuoteSnapshot {
  return {
    symbol,
    lastPrice,
    bid: null,
    ask: null,
    currency: 'USD',
    asOf: '2024-01-02T15:30:00Z',
    source: 'TEST',
  };
}

function batch(quotes: QuoteSnapshot[], missingSymbols: string[] = []): BatchQuotesResponse {
  return { quotes, missingSymbols, serverTimestamp: '2024-01-02T15:30:00Z' };
}

describe('DashboardComponent valuation', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let accountService: {
    getAccount: Mock;
    getBalance: Mock;
    getPositions: Mock;
    clearCache: Mock;
  };
  let marketData: { getBatchQuotes: Mock };

  beforeEach(() => {
    accountService = {
      getAccount: vi.fn().mockReturnValue(of(account)),
      getBalance: vi.fn().mockReturnValue(of({ accountId: 1, cashBalance: 400 })),
      getPositions: vi.fn().mockReturnValue(of([])),
      clearCache: vi.fn(),
    };
    marketData = { getBatchQuotes: vi.fn().mockReturnValue(of(batch([]))) };

    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: accountService },
        { provide: AuthService, useValue: { getAccountId: () => 1 } },
        { provide: MarketDataService, useValue: marketData },
        { provide: InstrumentService, useValue: { getInstruments: () => of([]) } },
      ],
    });

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('values positions from batch quotes and totals the portfolio', () => {
    accountService.getPositions.mockReturnValue(
      of([
        { accountId: 1, symbol: 'AAPL', quantity: 10, averageCost: 100 },
        { accountId: 1, symbol: 'MSFT', quantity: 5, averageCost: 200 },
      ])
    );
    marketData.getBatchQuotes.mockReturnValue(of(batch([quote('AAPL', 150), quote('MSFT', 220)])));

    component.loadDashboardData();
    const data = component.dashboardData!;

    expect(marketData.getBatchQuotes).toHaveBeenCalledWith(['AAPL', 'MSFT']);
    expect(data.positions[0]).toMatchObject({
      symbol: 'AAPL',
      currentMarketValue: 1500,
      gainLoss: 500,
      gainLossPercent: 50,
      quoteAvailable: true,
    });
    expect(data.positions[1]).toMatchObject({
      symbol: 'MSFT',
      currentMarketValue: 1100,
      gainLoss: 100,
      gainLossPercent: 10,
      quoteAvailable: true,
    });
    expect(data.totalMarketValue).toBe(2600);
    expect(data.portfolioCostBasis).toBe(2000);
    expect(data.unrealizedGainLoss).toBe(600);
    expect(data.unrealizedGainLossPercent).toBeCloseTo(30);
    expect(data.totalEquity).toBe(3000);
    expect(data.unavailableQuoteCount).toBe(0);
  });

  it('excludes a position without a quote from totals and cost basis', () => {
    accountService.getPositions.mockReturnValue(
      of([
        { accountId: 1, symbol: 'AAPL', quantity: 10, averageCost: 100 },
        { accountId: 1, symbol: 'XYZ', quantity: 4, averageCost: 50 },
      ])
    );
    marketData.getBatchQuotes.mockReturnValue(of(batch([quote('AAPL', 150)], ['XYZ'])));

    component.loadDashboardData();
    const data = component.dashboardData!;

    expect(data.positions[1]).toMatchObject({
      symbol: 'XYZ',
      currentMarketValue: 0,
      gainLoss: 0,
      gainLossPercent: 0,
      quoteAvailable: false,
    });
    expect(data.totalMarketValue).toBe(1500);
    expect(data.portfolioCostBasis).toBe(1000);
    expect(data.unrealizedGainLoss).toBe(500);
    expect(data.unrealizedGainLossPercent).toBeCloseTo(50);
    expect(data.totalEquity).toBe(1900);
    expect(data.unavailableQuoteCount).toBe(1);
  });

  it('reports zero percent gain when the cost basis is zero', () => {
    accountService.getPositions.mockReturnValue(
      of([{ accountId: 1, symbol: 'GIFT', quantity: 10, averageCost: 0 }])
    );
    marketData.getBatchQuotes.mockReturnValue(of(batch([quote('GIFT', 5)])));

    component.loadDashboardData();
    const data = component.dashboardData!;

    expect(data.positions[0]).toMatchObject({
      currentMarketValue: 50,
      gainLoss: 50,
      gainLossPercent: 0,
      quoteAvailable: true,
    });
    expect(data.portfolioCostBasis).toBe(0);
    expect(data.unrealizedGainLossPercent).toBe(0);
  });

  it('shows the generic error when the batch quote request fails', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    marketData.getBatchQuotes.mockReturnValue(throwError(() => new Error('Unable to load market quotes.')));

    component.loadDashboardData();

    expect(component.dashboardData).toBeNull();
    expect(component.error).toBe('Failed to load dashboard data. Please try again.');
    expect(component.isLoading).toBe(false);
  });

  it('shows the quote unavailable state and note in the template', () => {
    vi.useFakeTimers();
    try {
      accountService.getPositions.mockReturnValue(
        of([
          { accountId: 1, symbol: 'AAPL', quantity: 10, averageCost: 100 },
          { accountId: 1, symbol: 'XYZ', quantity: 4, averageCost: 50 },
        ])
      );
      marketData.getBatchQuotes.mockReturnValue(of(batch([quote('AAPL', 150)], ['XYZ'])));

      component.loadDashboardData();
      fixture.detectChanges();

      const text = fixture.nativeElement.textContent as string;
      expect(text).toContain('Quote unavailable');
      expect(text).toContain('1 position(s) without a market quote are excluded');
    } finally {
      vi.useRealTimers();
    }
  });
});
