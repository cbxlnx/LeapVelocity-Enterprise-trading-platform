import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { BatchQuotesResponse, QuoteSnapshot } from '../models/api.models';
import { MarketDataService, normalizeSymbol } from './market-data.service';

const aaplQuote: QuoteSnapshot = {
  symbol: 'AAPL',
  lastPrice: 187.5,
  bid: null,
  ask: null,
  currency: 'USD',
  asOf: '2024-01-02T15:30:00Z',
  source: 'TEST',
};

describe('MarketDataService', () => {
  let service: MarketDataService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MarketDataService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MarketDataService);
    http = TestBed.inject(HttpTestingController);
  });

  it('normalizes symbols by trimming and uppercasing', () => {
    expect(normalizeSymbol('  aapl ')).toBe('AAPL');
  });

  it('requests a single quote using the normalized symbol', async () => {
    const result = firstValueFrom(service.getQuote(' aapl '));
    const testRequest = http.expectOne('/api/v1/market-data/quotes/AAPL');

    expect(testRequest.request.method).toBe('GET');
    testRequest.flush(aaplQuote);

    await expect(result).resolves.toEqual(aaplQuote);
    http.verify();
  });

  it('turns a failed single quote request into a meaningful error', async () => {
    const result = firstValueFrom(service.getQuote('ZZZZ'));
    http.expectOne('/api/v1/market-data/quotes/ZZZZ').flush(
      {},
      { status: 404, statusText: 'Not Found' }
    );

    await expect(result).rejects.toThrow('Unable to load the market quote.');
    http.verify();
  });

  it('requests batch quotes with normalized, deduplicated symbols', async () => {
    const response: BatchQuotesResponse = {
      quotes: [aaplQuote],
      missingSymbols: [],
      serverTimestamp: '2024-01-02T15:30:00Z',
    };
    const result = firstValueFrom(service.getBatchQuotes([' aapl', 'MSFT', 'AAPL', '']));
    const testRequest = http.expectOne((request) => request.url === '/api/v1/market-data/quotes');

    expect(testRequest.request.method).toBe('GET');
    expect(testRequest.request.params.get('symbols')).toBe('AAPL,MSFT');
    testRequest.flush(response);

    await expect(result).resolves.toEqual(response);
    http.verify();
  });

  it('splits large batch requests at the API limit', async () => {
    const symbols = Array.from({ length: 51 }, (_, index) => `SYM${index}`);
    const result = firstValueFrom(service.getBatchQuotes(symbols));
    const requests = http.match((request) => request.url === '/api/v1/market-data/quotes');

    expect(requests).toHaveLength(2);
    expect(requests[0].request.params.get('symbols')?.split(',')).toHaveLength(50);
    expect(requests[1].request.params.get('symbols')?.split(',')).toHaveLength(1);

    requests[0].flush({ quotes: [aaplQuote], missingSymbols: [], serverTimestamp: 'first' });
    requests[1].flush({ quotes: [], missingSymbols: ['SYM50'], serverTimestamp: 'second' });

    await expect(result).resolves.toEqual({
      quotes: [aaplQuote],
      missingSymbols: ['SYM50'],
      serverTimestamp: 'second',
    });
    http.verify();
  });

  it('returns an empty batch without calling the server when there are no symbols', async () => {
    const result = await firstValueFrom(service.getBatchQuotes([]));

    expect(result).toEqual({ quotes: [], missingSymbols: [], serverTimestamp: '' });
    http.expectNone('/api/v1/market-data/quotes');
    http.verify();
  });
});
