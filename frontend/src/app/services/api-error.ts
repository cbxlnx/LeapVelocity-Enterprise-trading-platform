import { HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';

export function handleApiError(operation: string) {
  return (error: HttpErrorResponse): Observable<never> => {
    let message = `Unable to ${operation}.`;

    if (error.status === 0) {
      message = `Unable to ${operation}: the API is unreachable.`;
    } else if (typeof error.error?.message === 'string') {
      message = `${message} ${error.error.message}`;
    } else if (error.status >= 400 && error.status < 500) {
      message = `${message} The request was rejected (${error.status}).`;
    } else if (error.status >= 500) {
      message = `${message} The server returned an error (${error.status}).`;
    }

    return throwError(() => new Error(message));
  };
}