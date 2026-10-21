import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { handleApiError } from '../../../services/api-error';
import { HistoricalPrices } from '../models/historical-prices.model';
import { PortfolioSummary } from '../models/portfolio-summary.model';
import { PerformancePeriod, PortfolioPerformance } from '../models/portfolio-performance.model';
import { PortfolioRisk, PortfolioShock, RiskVarOptions } from '../models/portfolio-risk.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly http = inject(HttpClient);

  calculateShock(accountId: number, priceChangePercent: number): Observable<PortfolioShock> {
    return this.http.post<PortfolioShock>(`${environment.apiUrl}/v1/accounts/${accountId}/analytics/risk/shock`,
      { priceChangePercent }).pipe(catchError(handleApiError('calculate price shock')));
  }

  getRisk(accountId: number, period: PerformancePeriod,
    options: RiskVarOptions = { confidenceLevel: 0.95, holdingPeriodDays: 1 }): Observable<PortfolioRisk> {
    return this.http.get<PortfolioRisk>(`${environment.apiUrl}/v1/accounts/${accountId}/analytics/risk`,
      { params: { period, ...options } }).pipe(catchError(handleApiError('load portfolio risk')));
  }

  getPerformance(accountId: number, period: PerformancePeriod): Observable<PortfolioPerformance> {
    return this.http.get<PortfolioPerformance>(`${environment.apiUrl}/v1/accounts/${accountId}/analytics/performance`,
      { params: { period } }).pipe(catchError(handleApiError('load historical simulation')));
  }

  getSummary(accountId: number): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>(`${environment.apiUrl}/v1/accounts/${accountId}/analytics/summary`)
      .pipe(catchError(handleApiError('load portfolio summary')));
  }

  getPrices(symbol: string, from: string, to: string): Observable<HistoricalPrices> {
    const normalized = symbol.trim().toUpperCase();
    return this.http.get<HistoricalPrices>(
      `${environment.apiUrl}/v1/market-data/prices/${encodeURIComponent(normalized)}`,
      { params: { from, to } }
    ).pipe(catchError(handleApiError('load historical prices')));
  }
}
