import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AnalyticsApiService } from './analytics-api.service';

describe('AnalyticsApiService', () => {
  let api: AnalyticsApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AnalyticsApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('requests stored history with normalized symbol and inclusive date parameters', async () => {
    const result = firstValueFrom(api.getPrices(' aapl ', '2025-10-01', '2026-10-01'));
    const request = http.expectOne('/api/v1/market-data/prices/AAPL?from=2025-10-01&to=2026-10-01');
    expect(request.request.method).toBe('GET');
    const response = { symbol: 'AAPL', currency: 'USD', from: '2025-10-01', to: '2026-10-01', prices: [] };
    request.flush(response);
    await expect(result).resolves.toEqual(response);
  });

  it('reports server failures in a user-readable message', async () => {
    const result = firstValueFrom(api.getPrices('AAPL', '2025-10-01', '2026-10-01'));
    http.expectOne('/api/v1/market-data/prices/AAPL?from=2025-10-01&to=2026-10-01')
      .flush({}, { status: 500, statusText: 'Server Error' });
    await expect(result).rejects.toThrow('Unable to load historical prices. The server returned an error (500).');
  });
});
