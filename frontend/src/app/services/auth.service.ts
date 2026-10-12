import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { LoginRequest, LoginResponse } from './auth.service.interface';

/**
 * AuthService - Stub Implementation
 * 
 * This stub provides the basic structure for LoginComponent (Story 3.1).
 * Full implementation (HTTP calls, token management, etc.) will be completed in Story 1.2
 * by the authentication team.
 * 
 * TODO: Replace this entire file with full implementation in Story 1.2
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  login(request: LoginRequest): Observable<LoginResponse> {
    // TODO: Story 1.2 - Implement actual HTTP POST to /api/auth/login
    console.warn('AuthService.login() stub - replace in Story 1.2');
    return of({ accessToken: '', expiresIn: 0 });
  }

  isAuthenticated(): boolean {
    // TODO: Story 1.2 - Implement token validation
    return false;
  }

  getToken(): string | null {
    // TODO: Story 1.2 - Implement token retrieval
    return null;
  }

  logout(): void {
    // TODO: Story 1.2 - Implement logout logic
  }
}
