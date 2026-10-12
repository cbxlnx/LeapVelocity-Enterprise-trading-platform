import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { BehaviorSubject, Observable } from 'rxjs';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  isLoading$ = new BehaviorSubject<boolean>(false);
  errorMessage$ = new BehaviorSubject<string | null>(null);

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.initializeForm();
  }

  /**
   * Initialize reactive form with validation rules
   */
  private initializeForm(): void {
    this.loginForm = this.fb.group({
      username: [
        '',
        [
          Validators.required,
          Validators.minLength(3),
        ],
      ],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(6),
        ],
      ],
    });
  }

  /**
   * Handle form submission
   */
  onSubmit(): void {
    if (this.loginForm.invalid) {
      return;
    }

    this.isLoading$.next(true);
    this.errorMessage$.next(null);

    const { username, password } = this.loginForm.value;

    this.authService.login({ username, password }).subscribe({
      next: (response) => {
        this.isLoading$.next(false);
        // Redirect to dashboard on successful login
        this.router.navigate(['/dashboard']);
      },
      error: (error) => {
        this.isLoading$.next(false);
        this.errorMessage$.next(
          error?.error?.message || 'Login failed. Please try again.'
        );
      },
    });
  }

  /**
   * Get username form control
   */
  get username() {
    return this.loginForm.get('username');
  }

  /**
   * Get password form control
   */
  get password() {
    return this.loginForm.get('password');
  }

  /**
   * Check if username is required error
   */
  get usernameRequiredError(): boolean {
    return (
      (this.username?.hasError('required') ?? false) && (this.username?.touched ?? false)
    );
  }

  /**
   * Check if username is minlength error
   */
  get usernameMinlengthError(): boolean {
    return (
      (this.username?.hasError('minlength') ?? false) && (this.username?.touched ?? false)
    );
  }

  /**
   * Check if password is required error
   */
  get passwordRequiredError(): boolean {
    return (
      (this.password?.hasError('required') ?? false) && (this.password?.touched ?? false)
    );
  }

  /**
   * Check if password is minlength error
   */
  get passwordMinlengthError(): boolean {
    return (
      (this.password?.hasError('minlength') ?? false) && (this.password?.touched ?? false)
    );
  }

  /**
   * Check if submit button should be disabled
   */
  get isSubmitDisabled(): boolean {
    return this.loginForm.invalid || (this.isLoading$.value ?? false);
  }

  /**
   * Handle keydown event - submit on Enter key
   */
  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !this.isSubmitDisabled) {
      this.onSubmit();
    }
  }
}
