# Leap UI - Angular Frontend

A modern Angular 22 frontend application for trading and account management with standalone components, lazy-loaded routing, and modular feature-based architecture.

## Overview

This Angular application provides a user interface for the Leap trading platform, featuring:
- **Authentication**: Login and session management
- **Dashboard**: Account overview with balances and positions
- **Order Management**: Place and cancel trading orders
- **Order History**: View past orders in a blotter interface
- **Lazy Loading**: Feature modules load on-demand for improved performance
- **Production Optimized**: Configured for minimal bundle size and fast load times

## Prerequisites

Before you begin, ensure you have installed:
- **Node.js**: v18.0.0 or higher ([download](https://nodejs.org/))
- **npm**: v10.0.0 or higher (comes with Node.js)
- **Angular CLI**: v22.2.2 or higher

Verify installation:
```bash
node --version
npm --version
ng version
```

## Installation

1. **Navigate to the frontend directory**:
```bash
cd frontend
```

2. **Install dependencies**:
```bash
npm install
```

## Development Server

Start the development server:
```bash
npm start
```

or

```bash
ng serve
```

The application will be available at `http://localhost:4200/`. The application will automatically reload when you make changes to source files.

### Development with Live Reload

```bash
npm run watch
```

This builds the application in watch mode, updating the build with each file change.

## Build for Production

Create an optimized production build:

```bash
npm run build:prod
```

or

```bash
ng build --configuration production
```

The build artifacts will be stored in the `dist/leap-ui` directory.

### Build Output Optimization

Production builds include:
- ✅ Tree-shaking for unused code removal
- ✅ AOT (Ahead-of-Time) compilation
- ✅ Lazy loading of feature modules
- ✅ Source map disabled for smaller bundle
- ✅ Named chunks extracted for caching
- ✅ All assets hashed for cache busting

**Bundle Size Guidelines:**
- Initial bundle: < 500KB (warning), < 1MB (error)
- Component styles: < 12KB (warning), < 16KB (error)

## Project Structure

```
frontend/
├── src/
│   ├── app/
│   │   ├── features/              # Feature modules
│   │   │   ├── auth/             # Authentication feature
│   │   │   │   ├── login/        # Login component
│   │   │   │   └── auth.routes.ts
│   │   │   ├── dashboard/        # Dashboard feature
│   │   │   │   ├── dashboard/    # Dashboard component
│   │   │   │   └── dashboard.routes.ts
│   │   │   ├── order/            # Order placement feature
│   │   │   │   ├── order-ticket/ # Order form component
│   │   │   │   └── order.routes.ts
│   │   │   └── blotter/          # Order history feature
│   │   │       ├── blotter/      # Blotter table component
│   │   │       └── blotter.routes.ts
│   │   ├── shared/               # Shared components
│   │   │   └── not-found/        # 404 page component
│   │   ├── app.ts                # Root component
│   │   ├── app.config.ts         # Application configuration
│   │   ├── app.routes.ts         # Main routing configuration
│   │   └── app.css               # Global styles
│   ├── environments/              # Environment-specific configs
│   │   ├── environment.ts        # Development environment
│   │   └── environment.prod.ts   # Production environment
│   ├── index.html                # HTML entry point
│   ├── main.ts                   # Bootstrap file
│   └── styles.css                # Global stylesheets
├── public/                        # Static assets
├── dist/                          # Production build output
├── angular.json                   # Angular CLI configuration
├── tsconfig.json                  # TypeScript configuration
├── eslint.config.js              # ESLint configuration
├── .editorconfig                 # Editor settings
├── .gitignore                    # Git ignore rules
├── .prettierrc                   # Prettier formatting config
└── package.json                  # Project dependencies & scripts
```

## Feature Routes

The application uses lazy-loaded routes for optimized performance:

| Route | Feature | Component |
|-------|---------|-----------|
| `/login` | Authentication | LoginComponent |
| `/dashboard` | Dashboard | DashboardComponent |
| `/order` | Order Placement | OrderTicketComponent |
| `/blotter` | Order History | BlotterComponent |
| `/` | Root redirect | Redirects to `/dashboard` |
| `**` | Not Found | NotFoundComponent |

## Routing Configuration

Routes are defined in `src/app/app.routes.ts` with lazy loading:

```typescript
{
  path: 'login',
  loadChildren: () =>
    import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
}
```

Each feature has its own routing module (`auth.routes.ts`, `dashboard.routes.ts`, etc.) for modular organization.

## TypeScript Configuration

The project uses strict TypeScript compilation mode:

```json
{
  "strict": true,
  "noImplicitAny": true,
  "strictNullChecks": true,
  "strictFunctionTypes": true
}
```

This ensures type safety across the application.

## Environment Configuration

Environment-specific settings are managed in the `src/environments/` directory:

### Development (`environment.ts`)
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
};
```

### Production (`environment.prod.ts`)
```typescript
export const environment = {
  production: true,
  apiUrl: '/api',
};
```

Import and use in components:
```typescript
import { environment } from '../../environments/environment';

// Use: environment.apiUrl
```

## Code Quality & Linting

### Run ESLint

Check for code quality issues:

```bash
npm run lint
```

ESLint configuration: `eslint.config.js`
- Enforces TypeScript best practices
- Checks for unused variables, console statements
- Validates Angular component naming conventions

### Code Formatting

The project uses Prettier for consistent code formatting:

```bash
npx prettier --write src/**/*.{ts,html,css}
```

Configuration: `.prettierrc`

## Testing

### Unit Tests

Run unit tests with Vitest:

```bash
npm test
```

Tests automatically re-run on file changes.

## Standalone Components

This project uses Angular's standalone component syntax (no NgModule):

```typescript
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],  // Explicit imports
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {}
```

Benefits:
- Simpler component definitions
- Smaller bundle size
- Easier to tree-shake unused code

## Runtime Flow

```text
src/main.ts
  -> bootstrapApplication(App, appConfig)
      -> App shell
          -> optional HeaderComponent
          -> RouterOutlet
              -> lazy feature route
                  -> page component
                      -> injected service
                          -> HttpClient Observable
                              -> Angular proxy
                                  -> Spring Boot or Nest service
```

This follows the lecture sequence: Angular project structure, standalone components, dependency injection, HttpClient Observables, real backend calls, reactive forms, lazy routing, and authenticated JWT flows.

## Complete File Guide

The following sections describe the frontend files that contain application logic, templates, configuration, models, or tests. CSS files are intentionally omitted from the detailed discussion.

### Root Configuration

#### `angular.json`

Angular CLI/build configuration. It defines the `leap-ui` application, `src/main.ts` as the browser entry point, public assets, `src/styles.scss` as the active global stylesheet, production optimization, bundle budgets, and the development `proxy.conf.json`.

The build uses `@angular/build:application`, which compiles the application with Angular's modern esbuild/Vite-based tooling. Production budgets act as a guardrail against bundle growth.

#### `package.json`

Defines npm scripts and dependencies. Important packages include Angular core/router/forms, Angular Material, RxJS, TypeScript, ESLint, and Playwright. The `type: module` setting enables modern JavaScript module behavior.

#### `proxy.conf.json`

Development-only forwarding rules:

| Frontend request | Forwarded to |
| --- | --- |
| `/api/*` | Spring Boot at `127.0.0.1:8081` |
| `/auth/*` | Nest auth service at `127.0.0.1:4000` |

#### `tsconfig.json`, `tsconfig.app.json`, `tsconfig.spec.json`, `tsconfig.playwright.json`

TypeScript compiler configurations for shared application code, Angular application compilation, unit tests, and Playwright tests. Strict typing catches incorrect template bindings, nullable values, and mismatched API models before runtime.

#### `eslint.config.js`, `.prettierrc`, `.editorconfig`

These files define linting, formatting, and editor conventions. They are tooling files rather than runtime Angular code.

#### `playwright.config.ts`

Configures browser tests for Chromium, Firefox, and WebKit. It starts the Angular dev server when necessary, sets the base URL, and enables retry/trace behavior in CI.

### Application Bootstrap

#### `src/main.ts`

The browser entry point. `bootstrapApplication(App, appConfig)` starts the standalone Angular application and supplies its application-wide providers.

#### `src/index.html`

The single real HTML document loaded by the browser. Angular renders the bootstrapped application into its root element.

#### `src/app/app.config.ts`

Registers application-wide dependency-injection providers:

- `provideRouter(routes)` makes the route table available.
- `provideHttpClient(withInterceptors([authInterceptor]))` enables HttpClient and installs the JWT interceptor once for every service.
- `provideAnimations()` supports Angular Material.
- `provideBrowserGlobalErrorListeners()` installs browser error handling.

This is the same application-level provider pattern introduced with `provideRouter` and later reused for HttpClient.

#### `src/app/app.ts`

The root standalone component. It imports `RouterOutlet` and `HeaderComponent`, then derives `isAuthRoute$` from router `NavigationEnd` events with RxJS `filter`, `startWith`, and `map`. The header is hidden on login/register routes and visible elsewhere.

#### `src/app/app.html`

Contains the shell outlet:

```html
<app-header *ngIf="!(isAuthRoute$ | async)"></app-header>
<router-outlet></router-outlet>
```

`router-outlet` is where Angular inserts the currently active page component.

#### `src/app/app.spec.ts`

Root-component unit-test entry point. It is the place to test shell rendering and root-level behavior.

### Routing

#### `src/app/app.routes.ts`

The top-level URL-to-feature map. It lazy-loads feature route collections with `loadChildren` and lazily loads registration with `loadComponent`.

Current routes:

- `/login` -> auth route collection
- `/register` -> standalone register component
- `/dashboard` -> dashboard route collection
- `/order` -> order route collection
- `/blotter` -> blotter route collection
- `/` -> redirects to `/dashboard` with `pathMatch: 'full'`
- unknown paths -> `NotFoundComponent`

`pathMatch: 'full'` is required because an empty path otherwise matches the prefix of every URL.

#### `src/app/features/auth/auth.routes.ts`

Feature route collection for login and register. It keeps auth navigation modular and lazy-loadable from the root route table.

#### `src/app/features/dashboard/dashboard.routes.ts`

Feature route collection for the dashboard page.

#### `src/app/features/order/order.routes.ts`

Feature route collection for the order-ticket page.

#### `src/app/features/blotter/blotter.routes.ts`

Feature route collection for order history.

The route files are intentionally thin. The page components own page behavior; route definitions only map URLs to lazy feature entry points.

### Authentication Feature

#### `src/app/features/auth/login/login.component.ts`

Standalone reactive-form login component. It creates controls with `FormBuilder`, applies required/minimum-length validators, calls `AuthService.login()`, and navigates to `/dashboard` after success. It also reads `reason=session-expired` to explain why a user was returned to login.

This implements the lecture's real login flow: a reactive form calls a real auth endpoint, stores tokens through a service, then navigates away; errors remain in the component's UI state.

#### `src/app/features/auth/login/login.component.html`

Connects the template to the `FormGroup` using `[formGroup]`, `formControlName`, `(ngSubmit)`, async loading values, and validation messages. Template interpolation and event binding are Angular's HTML-plus-template-syntax layer.

#### `src/app/features/auth/login/login.component.spec.ts`

Unit tests for login initialization, validation, submission, and navigation behavior.

#### `src/app/features/auth/register/register.component.ts`

Standalone registration component. Its form intentionally matches the current `users` database contract: username, password, and password confirmation. It validates locally, then calls `AuthService.register()`.

#### `src/app/features/auth/register/register.component.html`

Renders the registration form, validation messages, loading state, security note, and navigation back to login.

### Dashboard Feature

#### `src/app/features/dashboard/dashboard/dashboard.component.ts`

Loads account, balance, and positions from `AccountService` with `forkJoin`, then maps raw API DTOs into the dashboard view model. It calculates total market value, equity, unrealized gain/loss, and gain/loss percentage.

It also demonstrates:

- `takeUntil(destroy$)` for subscription cleanup.
- `retry()` and `timeout()` for transient/slow API behavior.
- `finalize()` and a watchdog to prevent permanent loading states.
- `ChangeDetectorRef` updates for asynchronous completion in the current zoneless runtime.
- Explicit error and retry states.

#### `src/app/features/dashboard/dashboard/dashboard.component.html`

Renders account overview, balance/equity/P&L summary, action buttons, positions, and the market-overview placeholder. The template consumes the component's already-mapped view model rather than knowing backend DTO details.

### Order Feature

#### `src/app/features/order/order-ticket/order-ticket.component.ts`

Builds the reactive order form and submits real orders through `OrderService`. It supports typeable/searchable instruments, BUY/SELL state, quantity/price validation, live estimated values, idempotency keys, and backend success/error responses.

Client validators catch shape errors such as missing or non-positive values. The backend remains responsible for business rules such as unknown instruments or insufficient SELL holdings.

#### `src/app/features/order/order-ticket/order-ticket.component.html`

Connects the order form to the review panel. It displays authenticated account context, selected instrument details, calculated order value, submitting state, backend response, and server rejection messages.

### Blotter Feature

#### `src/app/features/blotter/blotter/blotter.component.ts`

Loads real orders through `AccountService.getOrders()`, then applies local symbol/side/status/date filters and pagination for the order-history view.

#### `src/app/features/blotter/blotter/blotter.component.html`

Renders the order-history page, filters, summary counts, refresh action, and order table.

### Shared Layout

#### `src/app/shared/layout/header/header.ts`

The shared authenticated header. It injects `Router` and `AuthService`, reads username/account ID from JWT claims, provides client-side navigation links, and clears tokens on logout.

#### `src/app/shared/layout/header/header.html`

Contains the enterprise brand, Dashboard/Order Ticket/Blotter `routerLink`s, dynamic account identity, and logout button. `routerLinkActive` supplies the active-page indicator without manual URL checks.

### Data Models

#### `src/app/models/api.models.ts`

TypeScript contracts matching backend API request/response shapes: accounts, balances, positions, orders, order requests, and order responses. Interfaces provide compile-time safety but do not validate runtime JSON.

#### `src/app/shared/models/account.model.ts`

UI-facing dashboard models. `DashboardData` aggregates API data and derived financial values so presentation components do not need to repeat business calculations.

### Services

#### `src/app/services/account.service.ts`

Real account HttpClient service. It exposes account, balance, position, and order Observables; caches responses with `shareReplay`; retries core account calls; converts errors with `catchError`; and can clear its cache for refresh.

#### `src/app/services/order.service.ts`

Real order HttpClient service. It posts and cancels orders, maps backend response variants into a stable frontend response, and uses shared API error handling.

#### `src/app/services/auth.service.ts`

Real auth HttpClient service for login, registration, refresh, JWT claim access, token storage, and logout. The refresh token exists specifically to replace expired access tokens without forcing a login every hour.

#### `src/app/services/auth.service.interface.ts`

Typed request/response interfaces for auth operations. These are compile-time TypeScript contracts used by `AuthService` and auth components.

#### `src/app/services/api-error.ts`

Shared RxJS error operator. It turns network, client, and server failures into short application messages instead of leaking raw framework errors into the UI.

#### `src/app/services/mock-account.service.ts`

Legacy seeded mock service from the early dashboard implementation. The dashboard now uses real `AccountService`; this file is retained for reference and should not be treated as the production data source.

#### `src/app/services/account.service.spec.ts`

Unit-test file for account HTTP calls and caching/error behavior.

#### `src/app/services/order.service.spec.ts`

Unit-test file for order request bodies, response mapping, and failure conversion.

### Interceptor

#### `src/app/interceptors/auth.interceptor.ts`

Functional `HttpInterceptorFn` that attaches the stored bearer token to protected requests, skips auth endpoints, refreshes an expired access token once after `401`, retries the original request, and redirects to login when the refresh session is also expired.

This is the shared-token/interceptor pattern from the authenticated-flows lecture. Services do not manually construct Authorization headers.

### Environment Files

#### `src/environments/environment.ts`

Development configuration. `apiUrl: '/api'` and `authUrl: ''` intentionally use proxy-relative paths.

#### `src/environments/environment.prod.ts`

Production configuration. It assumes the deployed web server or reverse proxy exposes the backend under `/api`.

### End-to-End Tests

#### `e2e/login.spec.ts`

Playwright browser tests for login rendering, labels, validation, disabled/enabled submit behavior, and keyboard submission. Some selectors/assertions reflect earlier prototype copy and should be updated when UI copy changes.

## Data And Authentication Flow

```text
LoginComponent
  -> AuthService.login()
  -> POST /auth/login
  -> access + refresh tokens stored
  -> router.navigate('/dashboard')

DashboardComponent
  -> AuthService.getAccountId()
  -> AccountService requests
  -> authInterceptor adds Bearer token
  -> /api proxy
  -> Spring Boot API
  -> forkJoin maps account/balance/positions
```

When an access token expires, the interceptor calls `/auth/refresh`, stores the replacement access token, and retries the failed request. If the refresh token has expired, the session is cleared and the login page explains that the session expired.

## Error-Handling Rules

- Reactive-form validation prevents malformed requests before the network call.
- Backend business-rule errors remain visible to the user through curated server messages.
- Network errors become readable API-unreachable messages.
- Dashboard requests have retries, timeouts, and visible Retry states.
- Failed refreshes redirect to login rather than leaving a stale authenticated page.
- Existing data should not be discarded merely because a later refresh fails.

## Useful Conceptual Rule

Keep display concerns in components and shared/API concerns in services. If a second component could need the same state, identity, cache, or request logic, it belongs in a root-provided service. Components should consume typed data and signals/Observables rather than duplicate service logic.
- More intuitive routing

## Lazy Loading & Performance

Feature modules are lazy-loaded via routing:

1. **Login module** loads on `/login`
2. **Dashboard module** loads on `/dashboard`
3. **Order module** loads on `/order`
4. **Blotter module** loads on `/blotter`

This strategy reduces the initial bundle and improves Time to Interactive (TTI).

## API Integration

The application communicates with the backend via HTTP:

**Expected API Base URL**: `http://localhost:8080/api` (dev) or `/api` (prod)

### Planned Services

- **AuthService**: Login, logout, token management
- **AccountService**: Account data, balance, positions
- **OrderService**: Place orders, cancel orders

### Mock API Setup

For development without a backend, use Mock Service Worker (MSW) or Angular testing utilities.

## Deployment

### Deploy to Production Server

1. **Build the application**:
```bash
npm run build:prod
```

2. **Copy `dist/leap-ui` to your web server** (nginx, Apache, etc.):
```bash
cp -r dist/leap-ui /var/www/leap-ui
```

3. **Configure web server routing**:
   - Serve `index.html` for all routes (for SPA routing)
   - Example nginx configuration:
     ```nginx
     location / {
       try_files $uri $uri/ /index.html;
     }
     ```

### Docker Deployment (Optional)

Create a `Dockerfile`:
```dockerfile
FROM node:18 as builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build:prod

FROM nginx:latest
COPY --from=builder /app/dist/leap-ui /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

Build and run:
```bash
docker build -t leap-ui:latest .
docker run -p 80:80 leap-ui:latest
```

## Troubleshooting

### Issue: "Module not found" errors

**Solution**: Ensure all imports match the actual file paths. Check for typos in:
- Import statements
- Feature module names
- Component file paths

### Issue: Build size too large

**Solution**: 
- Check for unused dependencies: `npm ls`
- Use `ng build --configuration production` for optimization
- Review lazy loading configuration in `app.routes.ts`

### Issue: ng serve fails to start

**Solution**:
```bash
# Clear Angular build cache
rm -rf .angular/

# Reinstall dependencies
rm -rf node_modules/
npm install

# Try serving again
npm start
```

### Issue: Port 4200 already in use

**Solution**: Use a different port:
```bash
ng serve --port 4300
```

## Contributing Guidelines

### Branch Naming
- Feature: `feature/component-name`
- Bug fix: `bugfix/issue-description`
- Story: `story/story-number`

### Commit Messages
```
feat: Add login component with form validation
fix: Correct routing redirect logic
chore: Update dependencies
```

### Pull Request Process
1. Create feature branch from `develop`
2. Make changes and commit
3. Ensure linting passes: `npm run lint`
4. Push to remote
5. Create pull request with description
6. Wait for code review and approval
7. Merge to develop

## Known Issues & Limitations

- Placeholder components require API integration
- No authentication guard implementation yet
- Error handling not fully implemented

## Future Improvements

- [ ] Implement AuthGuard for route protection
- [ ] Add HTTP interceptors for token handling
- [ ] Implement error handling service
- [ ] Add toast notifications
- [ ] Material Design components
- [ ] E2E tests with Playwright
- [ ] Internationalization (i18n)

## Resources

- [Angular Documentation](https://angular.dev)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)
- [RxJS Documentation](https://rxjs.dev/)
- [ESLint Configuration](https://eslint.org/docs/latest/use/configure/)

## License

This project is part of the Leap Trading Platform. See LICENSE file for details.

## Support

For issues, questions, or suggestions:
- Check existing issues in the repository
- Create a new issue with detailed description
- Contact the development team

---

**Last Updated**: 2026-10-12  
**Angular Version**: 22.2.0  
**TypeScript Version**: 5.x  
**Node Version**: 18+
