import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { getUsername: () => null, getAccountId: () => null } },
      ],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render router outlet host', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('should switch themes through the application shell toggle', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const toggle = fixture.nativeElement.querySelector('.theme-toggle') as HTMLButtonElement;

    expect(toggle).toBeTruthy();
    toggle.click();
    fixture.detectChanges();
    expect(document.documentElement.classList.contains('dark-theme')).toBeTruthy();

    toggle.click();
    fixture.detectChanges();
    expect(document.documentElement.classList.contains('dark-theme')).toBeFalsy();
  });

  it('should render the supplied header logo instead of the placeholder', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const header = fixture.nativeElement.querySelector('app-header') as HTMLElement;
    const logo = header.querySelector('img.logo') as HTMLImageElement;

    expect(logo).toBeTruthy();
    expect(logo.getAttribute('src')).toBe('logo.png');
    expect(logo.alt).toBe('');
    expect(header.querySelector('.brand-title')?.textContent?.trim()).toBe('Trade Velocity');
    expect(header.querySelector('.brand-text')).toBeNull();
  });

  it('should switch and persist the selected theme', () => {
    const service = TestBed.inject(ThemeService);
    localStorage.clear();

    service.setTheme('dark');

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.classList.contains('dark-theme')).toBeTruthy();
    expect(localStorage.getItem('leap-ui-theme')).toBe('dark');

    service.setTheme('light');
    expect(document.documentElement.classList.contains('dark-theme')).toBeFalsy();
  });
});
