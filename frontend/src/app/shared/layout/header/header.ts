import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { ThemeService } from '../../../services/theme.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './header.html',
  styleUrls: ['./header.css'],
})
export class HeaderComponent {
  @Input() variant: 'application' | 'welcome' = 'application';
  @ViewChild('menuToggle') private menuToggle?: ElementRef<HTMLButtonElement>;
  menuOpen = false;
  userName: string;
  accountId: string;

  constructor(
    private router: Router,
    private authService: AuthService,
    readonly themeService: ThemeService,
  ) {
    this.userName = this.authService.getUsername() ?? 'User';
    const accountId = this.authService.getAccountId();
    this.accountId = accountId
      ? `ACC-${String(accountId).padStart(3, '0')}`
      : 'Account unavailable';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  closeMenu(): void {
    if (this.menuOpen) {
      this.menuOpen = false;
      this.menuToggle?.nativeElement.focus();
    }
  }
}
