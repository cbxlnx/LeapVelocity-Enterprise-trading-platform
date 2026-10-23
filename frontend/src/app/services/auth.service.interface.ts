/**
 * Auth Service Interface
 * 
 * Full implementation will be provided in Story 1.2
 * This interface is used by LoginComponent (Story 3.1)
 */

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  expiresIn: number;
}

export interface RefreshResponse {
  accessToken: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}

export interface RegisterResponse {
  username: string;
  registered: boolean;
}

export interface IAuthService {
  login(request: LoginRequest): any; // Observable<LoginResponse> in Story 1.2
  isAuthenticated(): boolean;
  getToken(): string | null;
  logout(): void;
}
