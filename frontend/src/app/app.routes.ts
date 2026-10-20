import { Routes } from '@angular/router';
import { NotFoundComponent } from './shared/not-found/not-found.component';
import { SignedOutComponent } from './shared/signed-out/signed-out.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/dashboard/dashboard.routes').then((m) => m.DASHBOARD_ROUTES),
  },
  {
    path: 'analytics',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/analytics/pages/analytics-dashboard/analytics-dashboard.component').then(
        (m) => m.AnalyticsDashboardComponent,
      ),
  },
  {
    path: 'order',
    canActivate: [authGuard],
    loadChildren: () => import('./features/order/order.routes').then((m) => m.ORDER_ROUTES),
  },
  {
    path: 'blotter',
    canActivate: [authGuard],
    loadChildren: () => import('./features/blotter/blotter.routes').then((m) => m.BLOTTER_ROUTES),
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/settings/account-settings.component').then(
        (m) => m.AccountSettingsComponent
      ),
  },
  {
    path: 'signed-out',
    component: SignedOutComponent,
  },
  {
    path: '',
    title: 'Welcome | TradeVelocity',
    loadComponent: () =>
      import('./features/welcome/welcome-page.component').then((m) => m.WelcomePageComponent),
    pathMatch: 'full',
  },
  {
    path: '**',
    component: NotFoundComponent,
  },
];
