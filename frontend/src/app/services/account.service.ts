import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, shareReplay, catchError } from 'rxjs';
import { environment } from '../../environments/environment';
import { Account, Balance, Order, Position } from '../models/api.models';
import { handleApiError } from './api-error';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly accountsUrl = `${environment.apiUrl}/v1/accounts`;
  private readonly cache = new Map<string, Observable<unknown>>();

  constructor(private readonly http: HttpClient) {}

  getAccount(accountId: number): Observable<Account> {
    return this.getCached(`account:${accountId}`, () =>
      this.http.get<Account>(`${this.accountsUrl}/${accountId}`).pipe(
        catchError(handleApiError('load the account'))
      )
    );
  }

  getBalance(accountId: number): Observable<Balance> {
    return this.getCached(`balance:${accountId}`, () =>
      this.http.get<Balance>(`${this.accountsUrl}/${accountId}/balance`).pipe(
        catchError(handleApiError('load the account balance'))
      )
    );
  }

  getPositions(accountId: number): Observable<Position[]> {
    return this.getCached(`positions:${accountId}`, () =>
      this.http.get<Position[]>(`${this.accountsUrl}/${accountId}/positions`).pipe(
        catchError(handleApiError('load the account positions'))
      )
    );
  }

  getOrders(accountId: number): Observable<Order[]> {
    return this.getCached(`orders:${accountId}`, () =>
      this.http.get<Order[]>(`${this.accountsUrl}/${accountId}/orders`).pipe(
        catchError(handleApiError('load the account orders'))
      )
    );
  }

  clearCache(): void {
    this.cache.clear();
  }

  private getCached<T>(key: string, request: () => Observable<T>): Observable<T> {
    let result = this.cache.get(key) as Observable<T> | undefined;
    if (!result) {
      result = request().pipe(shareReplay({ bufferSize: 1, refCount: false }));
      this.cache.set(key, result);
    }
    return result;
  }
}