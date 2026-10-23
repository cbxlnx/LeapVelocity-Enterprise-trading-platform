import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from '../../../services/auth.service';
import { RiskDashboardApiService } from './risk-dashboard-api.service';

describe('RiskDashboardApiService', () => {
  let service: RiskDashboardApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getAccountId: () => 7 } },
      ],
    });
    service = TestBed.inject(RiskDashboardApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads risk data for the signed-in account with default analytics parameters', async () => {
    const payload = { portfolios: [] };
    const result = firstValueFrom(service.getDashboard());
    const request = http.expectOne('/risk-api/v1/accounts/7/risk/dashboard?lookbackDays=252&confidence=0.95');

    expect(request.request.method).toBe('GET');
    request.flush(payload);

    await expect(result).resolves.toEqual(payload);
  });

  it('loads the requested historical lookback window', async () => {
    const result = firstValueFrom(service.getDashboard(1260));
    const request = http.expectOne('/risk-api/v1/accounts/7/risk/dashboard?lookbackDays=1260&confidence=0.95');

    request.flush({ portfolios: [] });

    await expect(result).resolves.toEqual({ portfolios: [] });
  });

  it('posts custom shocks for the signed-in account', async () => {
    const shocks = { market: -10, Technology: 2, Financials: 0, Energy: -5, Healthcare: 0 };
    const payload = { lossUsd: 100, impactPercent: 1, postShockEquityUsd: 9900 };
    const result = firstValueFrom(service.runCustomShock(shocks));
    const request = http.expectOne('/risk-api/v1/accounts/7/risk/custom-shock');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(shocks);
    request.flush(payload);

    await expect(result).resolves.toEqual(payload);
  });

  it('fails before making a request when no account is signed in', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getAccountId: () => null } },
      ],
    });
    const unauthenticatedService = TestBed.inject(RiskDashboardApiService);

    await expect(firstValueFrom(unauthenticatedService.getDashboard())).rejects.toThrow('No signed-in account is available.');
  });
});
