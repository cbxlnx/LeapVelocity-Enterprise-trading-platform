import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { handleApiError } from '../../../services/api-error';
import { HistoricalPrices } from '../models/historical-prices.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly http = inject(HttpClient);

  getPrices(symbol: string, from: string, to: string): Observable<HistoricalPrices> {
    const normalized = symbol.trim().toUpperCase();
    return this.http.get<HistoricalPrices>(
      `${environment.apiUrl}/v1/market-data/prices/${encodeURIComponent(normalized)}`,
      { params: { from, to } }
    ).pipe(catchError(handleApiError('load historical prices')));
  }
}
