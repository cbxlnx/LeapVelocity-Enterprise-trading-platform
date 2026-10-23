import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getToken();

  if (!token || request.url.includes('/auth/')) {
    return next(request);
  }

  const authorizedRequest = request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });

  return next(authorizedRequest).pipe(
    catchError((error) => {
      if (error.status !== 401) {
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap(() => {
          const refreshedToken = authService.getToken();
          if (!refreshedToken) {
            return throwError(() => error);
          }

          return next(request.clone({
            setHeaders: {
              Authorization: `Bearer ${refreshedToken}`,
            },
          }));
        }),
        catchError(() => {
          authService.logout();
          void router.navigate(['/login'], {
            queryParams: { reason: 'session-expired' },
          });
          return throwError(() => error);
        })
      );
    })
  );
};