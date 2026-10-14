import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, shareReplay } from 'rxjs';
import { environment } from '../../environments/environment';
import { Instrument } from '../models/api.models';
import { handleApiError } from './api-error';

@Injectable({ providedIn: 'root' })
export class InstrumentService {
  private readonly instrumentsUrl = `${environment.apiUrl}/v1/instruments`;
  private readonly instruments$: Observable<Instrument[]>;

  constructor(private readonly http: HttpClient) {
    this.instruments$ = this.http.get<Instrument[]>(this.instrumentsUrl).pipe(
      catchError(handleApiError('load instruments')),
      shareReplay({ bufferSize: 1, refCount: false })
    );
  }

  getInstruments(): Observable<Instrument[]> {
    return this.instruments$;
  }
}
