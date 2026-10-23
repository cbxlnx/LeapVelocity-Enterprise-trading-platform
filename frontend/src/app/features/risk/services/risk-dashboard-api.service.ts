import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { handleApiError } from '../../../services/api-error';
import { AuthService } from '../../../services/auth.service';
import { RiskDashboardData, ShockResult, ShockValues } from '../models/risk-dashboard.model';

@Injectable({ providedIn: 'root' })
export class RiskDashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  getDashboard(): Observable<RiskDashboardData> {
    const accountId = this.auth.getAccountId();
    if (accountId === null) {
      return throwError(() => new Error('No signed-in account is available.'));
    }

    return this.http.get<RiskDashboardData>(
      `${environment.riskApiUrl}/v1/accounts/${accountId}/risk/dashboard`,
      { params: new HttpParams().set('lookbackDays', '252').set('confidence', '0.95') },
    ).pipe(catchError(handleApiError('load risk dashboard')));
  }

  runCustomShock(shocks: ShockValues): Observable<ShockResult> {
    const accountId = this.auth.getAccountId();
    if (accountId === null) {
      return throwError(() => new Error('No signed-in account is available.'));
    }

    return this.http.post<ShockResult>(
      `${environment.riskApiUrl}/v1/accounts/${accountId}/risk/custom-shock`,
      shocks,
    ).pipe(catchError(handleApiError('run custom shock')));
  }
}
