import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { AccountService } from './account.service';

describe('AccountService', () => {
  let service: AccountService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AccountService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountService);
    http = TestBed.inject(HttpTestingController);
  });

  it('loads an account and reuses the cached response', async () => {
    const request = service.getAccount(1);
    const cachedRequest = service.getAccount(1);

    const response = {
      id: 1,
      accountId: 'ACC-001',
      holderName: 'Jane Doe',
      cashBalance: 1000,
      status: 'ACTIVE',
      version: 1,
    };

    const result = firstValueFrom(request);
    const cachedResult = firstValueFrom(cachedRequest);
    const testRequest = http.expectOne('/api/v1/accounts/1');
    testRequest.flush(response);

    await expect(result).resolves.toEqual(response);
    await expect(cachedResult).resolves.toEqual(response);
    http.verify();
  });

  it('turns a not-found response into a meaningful error', async () => {
    const result = firstValueFrom(service.getBalance(99));
    http.expectOne('/api/v1/accounts/99/balance').flush(
      { message: 'Account not found' },
      { status: 404, statusText: 'Not Found' }
    );

    await expect(result).rejects.toThrow('Unable to load the account balance. Account not found');
    http.verify();
  });

  it('loads positions and orders from their account endpoints', async () => {
    const positions = [{ symbol: 'AAPL', quantity: 2 }];
    const orders = [{ id: 'order-1', symbol: 'AAPL', status: 'FILLED' }];

    const positionsResult = firstValueFrom(service.getPositions(7));
    const positionsRequest = http.expectOne('/api/v1/accounts/7/positions');
    positionsRequest.flush(positions);

    const ordersResult = firstValueFrom(service.getOrders(7));
    const ordersRequest = http.expectOne('/api/v1/accounts/7/orders');
    ordersRequest.flush(orders);

    await expect(positionsResult).resolves.toEqual(positions);
    await expect(ordersResult).resolves.toEqual(orders);
    http.verify();
  });

  it('submits a cash deposit request and returns the updated balance', async () => {
    const result = firstValueFrom(service.depositCash(7, 125.5));
    const request = http.expectOne('/api/v1/accounts/7/deposit');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ amount: 125.5 });
    request.flush({ accountId: 7, cashBalance: 874.5 });

    await expect(result).resolves.toEqual({ accountId: 7, cashBalance: 874.5 });
    http.verify();
  });

  it('clears cached requests so the next call goes to the server', async () => {
    const firstResult = firstValueFrom(service.getAccount(1));
    http.expectOne('/api/v1/accounts/1').flush({ id: 1 });
    await expect(firstResult).resolves.toEqual({ id: 1 });

    service.clearCache();

    const secondResult = firstValueFrom(service.getAccount(1));
    http.expectOne('/api/v1/accounts/1').flush({ id: 1, refreshed: true });
    await expect(secondResult).resolves.toEqual({ id: 1, refreshed: true });
    http.verify();
  });

  it('retries transient account failures before succeeding', async () => {
    const result = firstValueFrom(service.getBalance(1));
    const requests = http.match('/api/v1/accounts/1/balance');
    expect(requests).toHaveLength(1);
    requests[0].flush({}, { status: 500, statusText: 'Server Error' });

    await new Promise((resolve) => setTimeout(resolve, 1050));
    const retryRequest = http.expectOne('/api/v1/accounts/1/balance');
    retryRequest.flush({ cash: 100 });

    await expect(result).resolves.toEqual({ cash: 100 });
    http.verify();
  });
});