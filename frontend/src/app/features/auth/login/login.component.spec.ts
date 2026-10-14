import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../services/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: AuthService;
  let router: Router;

  beforeEach(async () => {
    // Create mock services
    const authServiceMock = {
      login: vi.fn(),
    };
    const routerMock = {
      navigate: vi.fn(),
    };
    const activatedRouteMock = {
      snapshot: {
        queryParamMap: {
          get: vi.fn().mockReturnValue(null),
        },
      },
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent, ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: activatedRouteMock },
      ],
    }).compileComponents();

    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Form Initialization', () => {
    it('should initialize form with empty username and password', () => {
      expect(component.loginForm.get('username')?.value).toBe('');
      expect(component.loginForm.get('password')?.value).toBe('');
    });

    it('should initialize form as invalid', () => {
      expect(component.loginForm.valid).toBeFalsy();
    });

    it('should initialize loading state as false', () => {
      expect(component.isLoading$.value).toBeFalsy();
    });

    it('should initialize error message as null', () => {
      expect(component.errorMessage$.value).toBeNull();
    });
  });

  describe('Username Validation', () => {
    it('should have username required error when empty', () => {
      const usernameControl = component.loginForm.get('username');
      usernameControl?.markAsTouched();
      fixture.detectChanges();

      expect(component.usernameRequiredError).toBeTruthy();
    });

    it('should have username minlength error when less than 3 characters', () => {
      const usernameControl = component.loginForm.get('username');
      usernameControl?.setValue('ab');
      usernameControl?.markAsTouched();
      fixture.detectChanges();

      expect(component.usernameMinlengthError).toBeTruthy();
    });

    it('should not have error when username is valid', () => {
      const usernameControl = component.loginForm.get('username');
      usernameControl?.setValue('validuser');
      usernameControl?.markAsTouched();
      fixture.detectChanges();

      expect(component.usernameRequiredError).toBeFalsy();
      expect(component.usernameMinlengthError).toBeFalsy();
    });

    it('should accept username with exactly 3 characters', () => {
      const usernameControl = component.loginForm.get('username');
      usernameControl?.setValue('abc');
      fixture.detectChanges();

      expect(usernameControl?.valid).toBeTruthy();
    });
  });

  describe('Password Validation', () => {
    it('should have password required error when empty', () => {
      const passwordControl = component.loginForm.get('password');
      passwordControl?.markAsTouched();
      fixture.detectChanges();

      expect(component.passwordRequiredError).toBeTruthy();
    });

    it('should have password minlength error when less than 6 characters', () => {
      const passwordControl = component.loginForm.get('password');
      passwordControl?.setValue('12345');
      passwordControl?.markAsTouched();
      fixture.detectChanges();

      expect(component.passwordMinlengthError).toBeTruthy();
    });

    it('should not have error when password is valid', () => {
      const passwordControl = component.loginForm.get('password');
      passwordControl?.setValue('validpass123');
      passwordControl?.markAsTouched();
      fixture.detectChanges();

      expect(component.passwordRequiredError).toBeFalsy();
      expect(component.passwordMinlengthError).toBeFalsy();
    });

    it('should accept password with exactly 6 characters', () => {
      const passwordControl = component.loginForm.get('password');
      passwordControl?.setValue('123456');
      fixture.detectChanges();

      expect(passwordControl?.valid).toBeTruthy();
    });
  });

  describe('Submit Button State', () => {
    it('should disable submit button when form is invalid', () => {
      expect(component.isSubmitDisabled).toBeTruthy();
    });

    it('should disable submit button when form is loading', () => {
      component.loginForm.get('username')?.setValue('validuser');
      component.loginForm.get('password')?.setValue('validpass123');
      component.isLoading$.next(true);
      fixture.detectChanges();

      expect(component.isSubmitDisabled).toBeTruthy();
    });

    it('should enable submit button when form is valid and not loading', () => {
      component.loginForm.get('username')?.setValue('validuser');
      component.loginForm.get('password')?.setValue('validpass123');
      component.isLoading$.next(false);
      fixture.detectChanges();

      expect(component.isSubmitDisabled).toBeFalsy();
    });
  });

  describe('Form Submission', () => {
    beforeEach(() => {
      component.loginForm.get('username')?.setValue('testuser');
      component.loginForm.get('password')?.setValue('testpass123');
    });

    it('should not submit when form is invalid', () => {
      component.loginForm.get('username')?.setValue('ab');
      component.onSubmit();

      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should call authService.login with correct credentials', () => {
      vi.mocked(authService.login).mockReturnValue(
        of({ accessToken: 'token123', expiresIn: 3600 })
      );

      component.onSubmit();

      expect(authService.login).toHaveBeenCalledWith({
        username: 'testuser',
        password: 'testpass123',
      });
    });

    it('should set loading state to true on submit', () => {
      vi.mocked(authService.login).mockReturnValue(
        of({ accessToken: 'token123', expiresIn: 3600 })
      );

      component.onSubmit();

      expect(component.isLoading$.value).toBeFalsy();
    });

    it('should clear error message on submit', () => {
      component.errorMessage$.next('Previous error');
      vi.mocked(authService.login).mockReturnValue(
        of({ accessToken: 'token123', expiresIn: 3600 })
      );

      component.onSubmit();

      expect(component.errorMessage$.value).toBeNull();
    });

    it('should navigate to dashboard on successful login', () => {
      vi.mocked(authService.login).mockReturnValue(
        of({ accessToken: 'token123', expiresIn: 3600 })
      );

      component.onSubmit();

      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('should display error message on login failure', () => {
      const errorResponse = { error: { message: 'Invalid credentials' } };
      vi.mocked(authService.login).mockReturnValue(throwError(() => errorResponse));

      component.onSubmit();

      expect(component.errorMessage$.value).toBe('Invalid credentials');
    });

    it('should display generic error message when no message in response', () => {
      vi.mocked(authService.login).mockReturnValue(throwError(() => ({})));

      component.onSubmit();

      expect(component.errorMessage$.value).toBe(
        'Login failed. Please try again.'
      );
    });

    it('should set loading state to false after successful login', () => {
      vi.mocked(authService.login).mockReturnValue(
        of({ accessToken: 'token123', expiresIn: 3600 })
      );

      component.onSubmit();

      expect(component.isLoading$.value).toBeFalsy();
    });

    it('should set loading state to false after failed login', () => {
      vi.mocked(authService.login).mockReturnValue(
        throwError(() => ({ error: { message: 'Login failed' } }))
      );

      component.onSubmit();

      expect(component.isLoading$.value).toBeFalsy();
    });
  });

  describe('Keyboard Navigation', () => {
    beforeEach(() => {
      component.loginForm.get('username')?.setValue('testuser');
      component.loginForm.get('password')?.setValue('testpass123');
      vi.mocked(authService.login).mockReturnValue(
        of({ accessToken: 'token123', expiresIn: 3600 })
      );
    });

    it('should submit form when Enter key is pressed and form is valid', () => {
      const event = new KeyboardEvent('keydown', { key: 'Enter' });

      component.onKeyDown(event);

      expect(authService.login).toHaveBeenCalled();
    });

    it('should not submit form when Enter key is pressed and form is invalid', () => {
      component.loginForm.get('username')?.setValue('ab');
      const event = new KeyboardEvent('keydown', { key: 'Enter' });

      component.onKeyDown(event);

      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should not submit form when other key is pressed', () => {
      const event = new KeyboardEvent('keydown', { key: 'Tab' });

      component.onKeyDown(event);

      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should not submit when loading', () => {
      component.isLoading$.next(true);
      const event = new KeyboardEvent('keydown', { key: 'Enter' });

      component.onKeyDown(event);

      expect(authService.login).not.toHaveBeenCalled();
    });
  });

  describe('Form Control Getters', () => {
    it('should return username control', () => {
      expect(component.username).toBe(component.loginForm.get('username'));
    });

    it('should return password control', () => {
      expect(component.password).toBe(component.loginForm.get('password'));
    });
  });
});
