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
- Component styles: < 4KB (warning), < 8KB (error)

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
