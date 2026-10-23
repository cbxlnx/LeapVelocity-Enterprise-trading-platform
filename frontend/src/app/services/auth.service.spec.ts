import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

function jwtWithPayload(payload: Record<string, unknown>): string {
  const encoded = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${encoded}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('logs in and stores access and refresh tokens', async () => {
    const result = firstValueFrom(service.login({ username: 'jane', password: 'secret' }));
    const request = http.expectOne('/auth/login');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'jane', password: 'secret' });
    request.flush({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 3600 });

    await expect(result).resolves.toEqual({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      expiresIn: 3600,
    });
    expect(service.getToken()).toBe('access-1');
    expect(localStorage.getItem('leap_refresh_token')).toBe('refresh-1');
  });

  it('stores only the access token when login has no refresh token', async () => {
    const result = firstValueFrom(service.login({ username: 'jane', password: 'secret' }));
    http.expectOne('/auth/login').flush({ accessToken: 'access-2', expiresIn: 60 });

    await expect(result).resolves.toEqual({ accessToken: 'access-2', expiresIn: 60 });
    expect(localStorage.getItem('leap_access_token')).toBe('access-2');
    expect(localStorage.getItem('leap_refresh_token')).toBeNull();
  });

  it('registers a user without changing stored tokens', async () => {
    const result = firstValueFrom(service.register({ username: 'new-user', password: 'secret' }));
    const request = http.expectOne('/auth/register');

    expect(request.request.method).toBe('POST');
    request.flush({ username: 'new-user', registered: true });

    await expect(result).resolves.toEqual({ username: 'new-user', registered: true });
    expect(service.getToken()).toBeNull();
  });

  it('refreshes the access token using the stored refresh token', async () => {
    localStorage.setItem('leap_refresh_token', 'refresh-1');
    const result = firstValueFrom(service.refreshAccessToken());
    const request = http.expectOne('/auth/refresh');

    expect(request.request.body).toEqual({ refreshToken: 'refresh-1' });
    request.flush({ accessToken: 'access-refreshed' });

    await expect(result).resolves.toEqual({ accessToken: 'access-refreshed' });
    expect(service.getToken()).toBe('access-refreshed');
  });

  it('throws when refreshing without a refresh token', () => {
    expect(() => service.refreshAccessToken()).toThrow('No refresh token is available.');
    http.expectNone('/auth/refresh');
  });

  it('reads authentication and account details from a valid token', () => {
    localStorage.setItem('leap_access_token', jwtWithPayload({ accountId: '42', username: ' jane ' }));

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getAccountId()).toBe(42);
    expect(service.getUsername()).toBe(' jane ');
  });

  it('returns null for invalid or unusable token payloads', () => {
    localStorage.setItem('leap_access_token', 'not-a-jwt');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.getAccountId()).toBeNull();
    expect(service.getUsername()).toBeNull();

    localStorage.setItem('leap_access_token', jwtWithPayload({ accountId: 0, username: '   ' }));
    expect(service.getAccountId()).toBeNull();
    expect(service.getUsername()).toBeNull();
  });

  it('clears both tokens on logout', () => {
    localStorage.setItem('leap_access_token', 'access');
    localStorage.setItem('leap_refresh_token', 'refresh');

    service.logout();

    expect(service.getToken()).toBeNull();
    expect(localStorage.getItem('leap_refresh_token')).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });
});
