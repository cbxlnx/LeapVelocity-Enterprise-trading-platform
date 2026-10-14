import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent implements OnInit {
  registerForm!: FormGroup;
  isLoading$ = new BehaviorSubject<boolean>(false);
  errorMessage$ = new BehaviorSubject<string | null>(null);

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.registerForm = this.fb.group(
      {
        username: ['', [Validators.required, Validators.minLength(3)]],
        password: ['', [Validators.required, Validators.minLength(8)]],
        confirmPassword: ['', [Validators.required]],
      },
      { validators: this.passwordsMatch }
    );
  }

  passwordsMatch(form: FormGroup): Record<string, boolean> | null {
    const password = form.get('password')?.value;
    const confirmPassword = form.get('confirmPassword')?.value;
    return password && confirmPassword && password !== confirmPassword
      ? { passwordsMismatch: true }
      : null;
  }

  controlHasError(controlName: string, errorName: string): boolean {
    const control = this.registerForm.get(controlName);
    return !!control?.hasError(errorName) && !!control.touched;
  }

  get passwordsMismatch(): boolean {
    return this.registerForm.hasError('passwordsMismatch') &&
      !!this.registerForm.get('confirmPassword')?.touched;
  }

  get isSubmitDisabled(): boolean {
    return this.registerForm.invalid || this.isLoading$.value;
  }

  onSubmit(): void {
    if (this.isSubmitDisabled) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.isLoading$.next(true);
    this.errorMessage$.next(null);

    const { username, password } = this.registerForm.getRawValue();
    this.authService.register({ username, password }).subscribe({
      next: () => {
        this.isLoading$.next(false);
        this.router.navigate(['/login']);
      },
      error: (error) => {
        this.isLoading$.next(false);
        this.errorMessage$.next(
          error?.error?.message || error?.message || 'Registration failed. Please try again.'
        );
      },
    });
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !this.isSubmitDisabled) {
      this.onSubmit();
    }
  }
}
