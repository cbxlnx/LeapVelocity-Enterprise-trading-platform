import { DOCUMENT } from '@angular/common';
import { Injectable, signal, inject } from '@angular/core';

export type Theme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'leap-ui-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  readonly theme = signal<Theme>(this.readTheme());

  constructor() {
    this.applyTheme(this.theme());
  }

  toggleTheme(): void {
    this.setTheme(this.theme() === 'light' ? 'dark' : 'light');
  }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
    this.applyTheme(theme);
    this.persistTheme(theme);
  }

  private readTheme(): Theme {
    const storedTheme = this.document.defaultView?.localStorage.getItem(THEME_STORAGE_KEY);
    return storedTheme === 'dark' ? 'dark' : 'light';
  }

  private applyTheme(theme: Theme): void {
    this.document.documentElement.classList.toggle('dark-theme', theme === 'dark');
    this.document.documentElement.style.colorScheme = theme;
  }

  private persistTheme(theme: Theme): void {
    this.document.defaultView?.localStorage.setItem(THEME_STORAGE_KEY, theme);
  }
}
