import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  RefreshResponse,
} from './auth.service.interface';

/**
 * Client for the Nest authentication service.
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly authUrl = `${environment.authUrl}/auth`;
  private readonly accessTokenKey = 'leap_access_token';
  private readonly refreshTokenKey = 'leap_refresh_token';

  constructor(private readonly http: HttpClient) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.authUrl}/login`, request).pipe(
      tap((response) => this.storeTokens(response))
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.authUrl}/register`, request);
  }

  refreshAccessToken(): Observable<RefreshResponse> {
    const refreshToken = localStorage.getItem(this.refreshTokenKey);
    if (!refreshToken) {
      throw new Error('No refresh token is available.');
    }

    return this.http
      .post<RefreshResponse>(`${this.authUrl}/refresh`, { refreshToken })
      .pipe(tap((response) => this.storeAccessToken(response.accessToken)));
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  getToken(): string | null {
    return localStorage.getItem(this.accessTokenKey);
  }

  getAccountId(): number | null {
    const accountId = Number(this.getTokenPayload()?.accountId);
      return Number.isInteger(accountId) && accountId > 0 ? accountId : null;
  }

  getUsername(): string | null {
    const username = this.getTokenPayload()?.username;
    return typeof username === 'string' && username.trim() ? username : null;
  }

  logout(): void {
    localStorage.removeItem(this.accessTokenKey);
    localStorage.removeItem(this.refreshTokenKey);
  }

  private storeTokens(response: LoginResponse): void {
    this.storeAccessToken(response.accessToken);
    if (response.refreshToken) {
      localStorage.setItem(this.refreshTokenKey, response.refreshToken);
    }
  }

  private storeAccessToken(accessToken: string): void {
    localStorage.setItem(this.accessTokenKey, accessToken);
  }

  private getTokenPayload(): { accountId?: number | string; username?: string } | null {
    const token = this.getToken();
    if (!token) {
      return null;
    }

    try {
      const encodedPayload = token.split('.')[1]
        .replace(/-/g, '+')
        .replace(/_/g, '/');
      const padding = (4 - encodedPayload.length % 4) % 4;
      const payload = JSON.parse(atob(encodedPayload.padEnd(encodedPayload.length + padding, '=')));
      return payload;
    } catch {
      return null;
    }
  }
}
