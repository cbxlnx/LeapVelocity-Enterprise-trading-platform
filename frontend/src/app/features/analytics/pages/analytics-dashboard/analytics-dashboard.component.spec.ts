import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HistoricalPrices } from '../../models/historical-prices.model';
import { AnalyticsApiService } from '../../services/analytics-api.service';
import { AnalyticsDashboardComponent } from './analytics-dashboard.component';

const sample: HistoricalPrices = {
  symbol: 'AAPL', currency: 'USD', from: '2025-10-01', to: '2025-10-02',
  prices: [
    { date: '2025-10-01', close: 250, adjustedClose: 248, volume: 100, source: 'yfinance' },
    { date: '2025-10-02', close: 253, adjustedClose: 251, volume: 200, source: 'yfinance' },
  ],
};

describe('Analytics dashboard', () => {
  let fixture: ComponentFixture<AnalyticsDashboardComponent>;
  let response: Subject<HistoricalPrices>;
  let api: { getPrices: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    response = new Subject<HistoricalPrices>();
    api = { getPrices: vi.fn().mockReturnValue(response) };
    await TestBed.configureTestingModule({
      imports: [AnalyticsDashboardComponent],
      providers: [{ provide: AnalyticsApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
  });

  it('shows loading, then plots the close rather than adjusted close', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading AAPL price history');
    response.next(sample);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('polyline')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('$253.00');
    expect(fixture.nativeElement.textContent).not.toContain('$251.00');
    expect(fixture.nativeElement.textContent).toContain('2 trading days');
  });

  it('shows an empty state without plotting fabricated prices', () => {
    response.next({ ...sample, prices: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No historical prices found');
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
  });

  it('shows errors and retries successfully', () => {
    response.error(new Error('API is unreachable'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('API is unreachable');
    const retry = new Subject<HistoricalPrices>();
    api.getPrices.mockReturnValue(retry);
    fixture.nativeElement.querySelector('.error-state button').click();
    fixture.detectChanges();
    expect(api.getPrices).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Loading AAPL price history');
    retry.next(sample);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('polyline')).toBeTruthy();
  });

  it('blocks reversed date ranges before sending another request', () => {
    fixture.componentInstance.dateForm.setValue({ from: '2026-10-01', to: '2025-10-01' });
    fixture.componentInstance.loadPrices();
    fixture.detectChanges();
    expect(api.getPrices).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('From must be on or before To');
  });

  it('cancels the previous request so old responses cannot replace the new range', () => {
    const next = new Subject<HistoricalPrices>();
    api.getPrices.mockReturnValue(next);
    fixture.componentInstance.dateForm.setValue({ from: sample.from, to: sample.to });
    fixture.componentInstance.loadPrices();
    response.next({ ...sample, prices: [] });
    expect(fixture.componentInstance.loading()).toBe(true);
    next.next(sample);
    expect(fixture.componentInstance.history()).toEqual(sample);
  });
});
