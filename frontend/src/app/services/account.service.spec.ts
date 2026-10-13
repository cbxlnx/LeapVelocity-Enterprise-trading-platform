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
    const testRequest = http.expectOne('http://localhost:8081/api/v1/accounts/1');
    testRequest.flush(response);

    await expect(result).resolves.toEqual(response);
    await expect(cachedResult).resolves.toEqual(response);
    http.verify();
  });

  it('turns a not-found response into a meaningful error', async () => {
    const result = firstValueFrom(service.getBalance(99));
    http.expectOne('http://localhost:8081/api/v1/accounts/99/balance').flush(
      { message: 'Account not found' },
      { status: 404, statusText: 'Not Found' }
    );

    await expect(result).rejects.toThrow('Unable to load the account balance. Account not found');
    http.verify();
  });
});