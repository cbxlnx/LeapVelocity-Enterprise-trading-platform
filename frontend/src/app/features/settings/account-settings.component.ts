import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from '../../services/account.service';
import { AuthService } from '../../services/auth.service';
import { Account, Balance } from '../../models/api.models';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-account-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './account-settings.component.html',
  styleUrl: './account-settings.component.css',
})
export class AccountSettingsComponent implements OnInit {
  account: Account | null = null;
  balance: Balance | null = null;
  loading = true;
  submitting = false;
  error: string | null = null;
  success: string | null = null;
  copied = false;
  usernameSaved = false;

  readonly depositForm: FormGroup;
  readonly usernameForm: FormGroup;

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly authService: AuthService,
    private readonly accountService: AccountService,
    private readonly changeDetector: ChangeDetectorRef
  ) {
    this.depositForm = this.formBuilder.nonNullable.group({
      amount: [0, [Validators.required, Validators.min(0.01)]],
    });
    this.usernameForm = this.formBuilder.nonNullable.group({
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(20), Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    });
  }

  get amountControl() {
    return this.depositForm.controls['amount'];
  }

  get usernameControl() {
    return this.usernameForm.controls['username'];
  }

  ngOnInit(): void {
    const accountId = this.authService.getAccountId();
    if (!accountId) {
      this.loading = false;
      this.error = 'Please sign in before loading account settings.';
      return;
    }

    forkJoin({
      account: this.accountService.getAccount(accountId),
      balance: this.accountService.getBalance(accountId),
    }).subscribe({
      next: ({ account, balance }) => {
        this.account = account;
        this.balance = balance;
        this.usernameForm.patchValue({ username: this.authService.getUsername() ?? '' });
        this.loading = false;
        this.changeDetector.detectChanges();
      },
      error: (error: Error) => {
        this.loading = false;
        this.error = error.message;
        this.changeDetector.detectChanges();
      },
    });
  }

  submitDeposit(): void {
    const accountId = this.authService.getAccountId();
    if (!accountId || this.depositForm.invalid || this.submitting) {
      this.depositForm.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.error = null;
    this.success = null;
    this.accountService.depositCash(accountId, Number(this.amountControl.value)).subscribe({
      next: (balance) => {
        this.balance = balance;
        this.submitting = false;
        this.depositForm.reset({ amount: 0 });
        this.success = 'Cash added successfully. Your updated balance is ready for trading.';
        this.accountService.clearCache();
      },
      error: (error: Error) => {
        this.submitting = false;
        this.error = error.message;
      },
    });
  }

  selectDepositAmount(amount: number): void {
    this.depositForm.patchValue({ amount });
    this.amountControl.markAsDirty();
  }

  copyAccountId(): void {
    if (!this.account) {
      return;
    }

    const accountId = this.account.accountId;
    const clipboard = typeof navigator !== 'undefined' ? navigator.clipboard : undefined;
    const copyPromise = clipboard?.writeText
      ? clipboard.writeText(accountId)
      : Promise.resolve(this.copyWithFallback(accountId));

    copyPromise.then((copied) => {
      if (copied !== false) {
        this.copied = true;
        setTimeout(() => (this.copied = false), 1800);
      }
    });
  }

  private copyWithFallback(value: string): boolean {
    const textArea = document.createElement('textarea');
    textArea.value = value;
    textArea.setAttribute('readonly', '');
    textArea.style.position = 'fixed';
    textArea.style.opacity = '0';
    document.body.appendChild(textArea);
    textArea.select();
    const copied = document.execCommand('copy');
    document.body.removeChild(textArea);
    return copied;
  }

  saveUsername(): void {
    if (this.usernameForm.invalid) {
      this.usernameForm.markAllAsTouched();
      return;
    }
    this.usernameSaved = true;
    setTimeout(() => (this.usernameSaved = false), 2200);
  }

  formatCurrency(value: number | null): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value ?? 0);
  }
}