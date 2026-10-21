import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HistoricalPrices } from '../../models/historical-prices.model';
import { AnalyticsApiService } from '../../services/analytics-api.service';
import { AnalyticsDashboardComponent } from './analytics-dashboard.component';
import { AuthService } from '../../../../services/auth.service';
import { InstrumentService } from '../../../../services/instrument.service';
import { PortfolioPerformance } from '../../models/portfolio-performance.model';
import { provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { SVGRenderer } from 'echarts/renderers';

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, SVGRenderer]);

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  } as typeof ResizeObserver;
}

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
  let api: { getPrices: ReturnType<typeof vi.fn>; getSummary: ReturnType<typeof vi.fn>; getPerformance: ReturnType<typeof vi.fn> };
  let instruments: { getInstruments: ReturnType<typeof vi.fn> };
  const catalog = [
    { id: 2, symbol: 'MSFT', name: 'Microsoft Corporation', assetClass: 'EQUITY', currency: 'USD', tradable: true },
    { id: 1, symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  ];
  const emptyPerformance: PortfolioPerformance = { accountId: 1, period: '1Y', benchmark: 'SPY', simulationStatus: 'EMPTY',
    weightingDate: null, requestedFrom: null, requestedTo: null, from: null, to: null,
    portfolioReturnPercent: null, benchmarkReturnPercent: null, initialWeights: [], points: [], issues: [] };
  const emptySummary = { accountId: 1, currency: 'USD', asOf: null, valuationStatus: 'EMPTY',
    holdingsValue: 0, cashBalance: 5, totalEquity: 5, costBasis: 0, unrealizedPnL: 0, holdings: [], issues: [] };

  beforeEach(async () => {
    response = new Subject<HistoricalPrices>();
    instruments = { getInstruments: vi.fn().mockReturnValue(of(catalog)) };
    api = { getPrices: vi.fn().mockReturnValue(response), getSummary: vi.fn().mockReturnValue(of(emptySummary)),
      getPerformance: vi.fn().mockReturnValue(of(emptyPerformance)) };
    await TestBed.configureTestingModule({
      imports: [AnalyticsDashboardComponent],
      providers: [
        { provide: AnalyticsApiService, useValue: api },
        { provide: AuthService, useValue: { getAccountId: vi.fn().mockReturnValue(1) } },
        { provide: InstrumentService, useValue: instruments },
        provideEchartsCore({ echarts }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
  });

  it('shows loading, then plots the close rather than adjusted close', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading AAPL price history');
    response.next(sample);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.price-chart')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('$253.00');
    expect(fixture.nativeElement.textContent).not.toContain('$251.00');
    expect(fixture.nativeElement.textContent).toContain('2 session observations');
  });

  it('shows an empty state without plotting fabricated prices', () => {
    response.next({ ...sample, prices: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No historical prices found');
    expect(fixture.nativeElement.querySelector('.price-chart')).toBeNull();
  });

  it('shows errors and retries successfully', () => {
    response.error(new Error('API is unreachable'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('API is unreachable');
    const retry = new Subject<HistoricalPrices>();
    api.getPrices.mockReturnValue(retry);
    fixture.nativeElement.querySelector('.error-state button').click();
    fixture.detectChanges();
    expect(api.getPrices).toHaveBeenCalledTimes(4);
    expect(fixture.nativeElement.textContent).toContain('Loading AAPL price history');
    retry.next(sample);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.price-chart')).toBeTruthy();
  });

  it('blocks reversed date ranges before sending another request', () => {
    fixture.componentInstance.dateForm.patchValue({ from: '2026-10-01', to: '2025-10-01' });
    fixture.componentInstance.loadPrices();
    fixture.detectChanges();
    expect(api.getPrices).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Start date must be on or before end date');
  });

  it('cancels the previous request so old responses cannot replace the new range', () => {
    const next = new Subject<HistoricalPrices>();
    api.getPrices.mockReturnValue(next);
    fixture.componentInstance.dateForm.patchValue({ from: sample.from, to: sample.to });
    fixture.componentInstance.loadPrices();
    response.next({ ...sample, prices: [] });
    expect(fixture.componentInstance.loading()).toBe(true);
    next.next(sample);
    expect(fixture.componentInstance.history()).toEqual(sample);
  });

  it('keeps instrument history usable when summary loading fails', () => {
    api.getSummary.mockReturnValue(throwError(() => new Error('Summary unavailable')));
    fixture.componentInstance.loadSummary();
    response.next(sample);
    fixture.detectChanges();
    expect(api.getSummary).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.price-chart')).toBeTruthy();
  });

  it('loads only the selected instrument and ignores a response for the previous symbol', () => {
    const next = new Subject<HistoricalPrices>();
    api.getPrices.mockReturnValue(next);
    fixture.componentInstance.dateForm.patchValue({ from: sample.from, to: sample.to });
    const select = fixture.nativeElement.querySelector('#price-symbol');
    select.value = 'MSFT';
    select.dispatchEvent(new Event('change'));
    response.next(sample);
    fixture.detectChanges();
    expect(api.getPrices).toHaveBeenCalledTimes(5);
    expect(api.getPrices).toHaveBeenCalledWith('MSFT', sample.from, sample.to);
    expect(fixture.componentInstance.history()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Loading MSFT price history');
    expect(fixture.nativeElement.textContent).toContain('MSFT · Historical price');
    next.next({ ...sample, symbol: 'MSFT', prices: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No MSFT prices are stored');
    expect(api.getSummary).toHaveBeenCalledTimes(1);
    expect(api.getPerformance).toHaveBeenCalledTimes(1);
  });

  it('uses the returned catalog even when AAPL is absent', () => {
    instruments.getInstruments.mockReturnValue(of([catalog[0]]));
    fixture.componentInstance.loadInstruments();
    fixture.detectChanges();
    expect(api.getPrices).toHaveBeenCalledWith('MSFT', expect.any(String), expect.any(String));
    expect(fixture.nativeElement.querySelector('#price-symbol option').textContent).toContain('Microsoft');
  });

  it('can retry an instrument lookup failure without disturbing portfolio requests', () => {
    instruments.getInstruments.mockReturnValue(throwError(() => new Error('Instrument list unavailable')));
    fixture.componentInstance.loadInstruments();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.instrument-error').textContent).toContain('Instrument list unavailable');
    instruments.getInstruments.mockReturnValue(of(catalog));
    fixture.nativeElement.querySelector('.instrument-error button').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#price-symbol').options.length).toBe(2);
    expect(api.getSummary).toHaveBeenCalledTimes(1);
  });

  it('shows an empty catalog without requesting an unsupported symbol', () => {
    instruments.getInstruments.mockReturnValue(of([]));
    fixture.componentInstance.loadInstruments();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No eligible USD instruments');
    expect(fixture.nativeElement.querySelector('app-price-chart')).toBeNull();
    expect(api.getPrices).toHaveBeenCalledTimes(3);
  });

  it('does not guess an account id when the token lacks one', () => {
    vi.mocked(TestBed.inject(AuthService).getAccountId).mockReturnValue(null);
    fixture.componentInstance.loadSummary();
    fixture.detectChanges();
    expect(api.getSummary).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.summaryError()).toContain('Please sign in again');
  });

  it('cancels an old simulation request when a new period is selected', () => {
    const old = new Subject<PortfolioPerformance>(), current = new Subject<PortfolioPerformance>();
    api.getPerformance.mockReturnValueOnce(old).mockReturnValueOnce(current);
    fixture.componentInstance.changePerformancePeriod('1M');
    fixture.componentInstance.changePerformancePeriod('2Y');
    old.next(emptyPerformance);
    expect(fixture.componentInstance.performanceLoading()).toBe(true);
    current.next({ ...emptyPerformance, period: '2Y' });
    expect(fixture.componentInstance.performance()?.period).toBe('2Y');
    expect(api.getPerformance).toHaveBeenLastCalledWith(1, '2Y');
  });

  it('keeps summary and instrument prices available when simulation fails', () => {
    api.getPerformance.mockReturnValue(throwError(() => new Error('Simulation unavailable')));
    fixture.componentInstance.loadPerformance();
    response.next(sample);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-performance-panel').textContent).toContain('Simulation unavailable');
    expect(api.getSummary).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.price-chart')).toBeTruthy();
  });
});
