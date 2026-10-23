import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { BatchQuotesResponse, QuoteSnapshot } from '../models/api.models';
import { handleApiError } from './api-error';

export function normalizeSymbol(symbol: string): string {
  return symbol.trim().toUpperCase();
}

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private static readonly maxSymbolsPerRequest = 50;
  private readonly quotesUrl = `${environment.apiUrl}/v1/market-data/quotes`;

  constructor(private readonly http: HttpClient) {}

  getQuote(symbol: string): Observable<QuoteSnapshot> {
    const normalized = normalizeSymbol(symbol);
    return this.http
      .get<QuoteSnapshot>(`${this.quotesUrl}/${encodeURIComponent(normalized)}`)
      .pipe(catchError(handleApiError('load the market quote')));
  }

  getBatchQuotes(symbols: string[]): Observable<BatchQuotesResponse> {
    const uniqueSymbols = [
      ...new Set(symbols.map(normalizeSymbol).filter((symbol) => symbol.length > 0)),
    ];

    if (uniqueSymbols.length === 0) {
      return of({ quotes: [], missingSymbols: [], serverTimestamp: '' });
    }

    const chunks: string[][] = [];
    for (let index = 0; index < uniqueSymbols.length; index += MarketDataService.maxSymbolsPerRequest) {
      chunks.push(uniqueSymbols.slice(index, index + MarketDataService.maxSymbolsPerRequest));
    }

    return forkJoin(chunks.map((chunk) => this.http.get<BatchQuotesResponse>(this.quotesUrl, {
      params: { symbols: chunk.join(',') },
    }))).pipe(
      map((responses) => ({
        quotes: responses.flatMap((response) => response.quotes),
        missingSymbols: responses.flatMap((response) => response.missingSymbols),
        serverTimestamp: responses.at(-1)?.serverTimestamp ?? '',
      }))
    )
      .pipe(catchError(handleApiError('load market quotes')));
  }
}
