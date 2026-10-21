import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, MarkLineComponent, MarkPointComponent, TooltipComponent } from 'echarts/components';
import { SVGRenderer } from 'echarts/renderers';
import { RiskDashboardComponent } from './risk-dashboard.component';
import { AuthService } from '../../../../services/auth.service';
import { replayFixture, riskFixture, shockFixture, TEST_INSTRUMENTS } from '../../testing/risk-dashboard.fixture';
import { HistoricalReplay, PortfolioRisk } from '../../../analytics/models/portfolio-risk.model';
import { RiskHorizon } from '../../models/risk-dashboard.model';

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, MarkLineComponent, MarkPointComponent, SVGRenderer]);
if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class { observe(): void {} unobserve(): void {} disconnect(): void {} } as typeof ResizeObserver;
}

describe('Risk dashboard with actual API responses', () => {
  let fixture: ComponentFixture<RiskDashboardComponent>;
  let http: HttpTestingController;
  const url = (period = '1Y', horizon = 1) => `/api/v1/accounts/7/analytics/risk?period=${period}&confidenceLevel=0.95&holdingPeriodDays=${horizon}`;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RiskDashboardComponent], providers: [provideRouter([]), provideEchartsCore({ echarts }),
      provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { getAccountId: () => 7 } }],
    }).compileComponents();
    fixture = TestBed.createComponent(RiskDashboardComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/instruments').flush(TEST_INSTRUMENTS);
  });
  afterEach(() => { http.verify(); fixture.destroy(); });

  function finishReplays(response: (id: string) => HistoricalReplay = id => replayFixture(7, id)): void {
    const requests = http.match(request => request.url.includes('/analytics/risk/replay/'));
    expect(requests).toHaveLength(2);
    for (const request of requests) {
      expect(request.request.method).toBe('POST');
      request.flush(response(request.request.url.split('/').pop()!));
    }
    fixture.detectChanges();
  }
  function loadRisk(response: PortfolioRisk = riskFixture()): void {
    http.expectOne(url()).flush(response);
    fixture.detectChanges();
    finishReplays();
  }

  it('connects KPIs, VaR, drawdown, insights and holdings to one authenticated snapshot', () => {
    loadRisk();
    const summary = fixture.nativeElement.querySelector('.kpi-grid').textContent;
    for (const value of ['$1,100.00', '$50.00', '$60.00', '$55.00', '15.25%', '0.80', '−20.00%']) expect(summary).toContain(value);
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Synthetic Alpha');
    expect(text).toContain('AAA · 60.00%');
    expect(text).not.toContain('MSFT');
    expect(text).not.toContain('44,935');
    for (const removed of ['Not yet assessed', 'No result yet', 'no result yet', 'Volatility contribution', 'By sector', 'Sector exposure', 'Sector increments', 'Holding VaR', 'Holding volatility']) expect(text).not.toContain(removed);
    expect(fixture.nativeElement.querySelector('.contribution-card')).toBeNull();
    expect(fixture.nativeElement.querySelector('.waterfall-section')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.concentration-card thead th')).toHaveLength(3);
    expect(text).toContain('Expected Shortfall');
    expect(text).toContain('$70.00');
    expect(fixture.nativeElement.querySelector('.drawdown-chart')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.histogram-chart')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.var-card').textContent).not.toContain('30 days');
  });

  it('refreshes, changes the actual history/horizon, clears old shocks and cancels stale requests', () => {
    http.expectOne(url()).flush(riskFixture());
    fixture.componentInstance.updateWindow('3M');
    const stale = http.expectOne(url('3M'));
    fixture.componentInstance.updateWindow('6M');
    expect(stale.cancelled).toBe(true);
    http.expectOne(url('6M')).flush(riskFixture(7, '6M'));
    fixture.componentInstance.updateHorizon(10);
    http.expectOne(url('6M', 10)).flush(riskFixture(7, '6M'));
    fixture.componentInstance.loadDashboard();
    expect(fixture.componentInstance.risk()).toBeNull();
    http.expectOne(url('6M', 10)).flush(riskFixture(7, '6M'));
  });

  it('keeps errors explicit and never falls back to demo portfolios', () => {
    http.expectOne(url()).flush({}, { status: 500, statusText: 'Service unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Account risk data could not be loaded');
    expect(fixture.nativeElement.querySelector('.kpi-grid')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('ACC-001');
    expect(fixture.componentInstance.portfolio()).toBeNull();
    finishReplays();
  });

  it('keeps custom shocks usable when the Python risk request fails', () => {
    http.expectOne(url()).flush({}, { status: 500, statusText: 'Risk service unavailable' });
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('[aria-label="Market shock percentage"]');
    input.value = '-10'; input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('.shock-card button.primary').click();
    http.expectOne('/api/v1/accounts/7/analytics/risk/shock').flush(shockFixture());
    finishReplays();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.breakdown-table').textContent).toContain('$540.00');
    expect(fixture.componentInstance.customResult()?.totalPnL).toBe(-100);
    expect(fixture.nativeElement.querySelector('.kpi-grid')).toBeNull();
  });

  it('cancels background replay checks on refresh and validates the new snapshot', () => {
    http.expectOne(url()).flush(riskFixture()); fixture.detectChanges();
    const pending = http.match(request => request.url.includes('/analytics/risk/replay/'));
    expect(pending).toHaveLength(2);
    fixture.componentInstance.loadDashboard(); fixture.detectChanges();
    expect(pending.every(request => request.cancelled)).toBe(true);
    loadRisk();
    expect(fixture.nativeElement.querySelectorAll('.historical-scenario')).toHaveLength(2);
  });

  it('keeps historical replay independent of a failed Python risk request', () => {
    http.expectOne(url()).flush({}, { status: 500, statusText: 'Risk service unavailable' });
    fixture.detectChanges(); finishReplays();
    fixture.nativeElement.querySelector('[aria-label="View breakdown MARCH_2020"]').click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.breakdown-table').textContent).toContain('$540.00');
    expect(fixture.nativeElement.querySelector('.kpi-grid')).toBeNull();
  });

  it('runs a historical replay and populates the same backend holding breakdown', () => {
    loadRisk();
    fixture.nativeElement.querySelector('[aria-label="Run replay MARCH_2020"]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Checking historical replay coverage');
    const request = http.expectOne('/api/v1/accounts/7/analytics/risk/replay/MARCH_2020');
    expect(request.request.method).toBe('POST');
    request.flush(replayFixture()); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.breakdown-table').textContent).toContain('$540.00');
    expect(fixture.nativeElement.querySelector('.stress-card').textContent).toContain('2020-02-28');
    expect(fixture.componentInstance.selectedScenario()?.totalPnL).toBe(-100);
    expect(fixture.componentInstance.customResult()).toBeNull();
  });

  it('assesses both events before showing their cards and waits for selection before showing a breakdown', () => {
    http.expectOne(url()).flush(riskFixture()); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.stress-card')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Checking historical replay coverage');
    finishReplays();
    expect(fixture.nativeElement.querySelectorAll('.historical-scenario')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.waterfall-section')).toBeNull();
    fixture.nativeElement.querySelector('[aria-label="View breakdown MARCH_2020"]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.breakdown-table').textContent).toContain('$540.00');
    expect(fixture.componentInstance.selectedScenario()?.totalPnL).toBe(-100);
    http.expectNone(request => request.url.includes('/replay/'));
  });

  it.each(['UNAVAILABLE', 'EMPTY', 'NOT_IMPLEMENTED'] as const)('hides %s scenarios without displaying an empty section or a fabricated total', status => {
    http.expectOne(url()).flush(riskFixture()); fixture.detectChanges();
    finishReplays(id => ({ ...replayFixture(7, id), status, totalPnL: null, validatedHoldings: 1, reasons: ['MISSING_EVENT_PRICE'] }));
    expect(fixture.nativeElement.querySelector('.stress-card')).toBeNull();
    expect(fixture.nativeElement.querySelector('.waterfall-section')).toBeNull();
    expect(fixture.nativeElement.querySelector('.replay-loading')).toBeNull();
    expect(fixture.componentInstance.selectedScenario()).toBeNull();
  });

  it('keeps a supported event when another event has an unsupported holding or missing observations', () => {
    http.expectOne(url()).flush(riskFixture()); fixture.detectChanges();
    finishReplays(id => id === 'MARCH_2020' ? replayFixture() : { ...replayFixture(7, id), status: 'UNAVAILABLE', totalPnL: null, validatedHoldings: 1 });
    expect(fixture.nativeElement.querySelectorAll('.historical-scenario')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('[aria-label="Run replay CRISIS_2008"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label="Run replay MARCH_2020"]')).toBeTruthy();
  });

  it('does not request or render scenarios explicitly marked unimplemented', () => {
    const response = riskFixture();
    response.stressCapabilities!.historical.forEach(row => row.status = 'NOT_IMPLEMENTED');
    http.expectOne(url()).flush(response); fixture.detectChanges();
    http.expectNone(request => request.url.includes('/replay/'));
    expect(fixture.nativeElement.querySelector('.stress-card')).toBeNull();
  });

  it.each(['error', 'wrong-account', 'incomplete'])('preserves a retry for %s replay failures without a fabricated total', state => {
    http.expectOne(url()).flush(riskFixture()); fixture.detectChanges();
    http.expectOne('/api/v1/accounts/7/analytics/risk/replay/CRISIS_2008').flush({ ...replayFixture(7, 'CRISIS_2008'), status: 'UNAVAILABLE', totalPnL: null });
    const request = http.expectOne('/api/v1/accounts/7/analytics/risk/replay/MARCH_2020');
    if (state === 'error') request.flush({}, { status: 500, statusText: 'Calculation failed' });
    else {
      const response = replayFixture(state === 'wrong-account' ? 8 : 7);
      if (state === 'incomplete') response.validatedHoldings = 1;
      request.flush(response);
    }
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.breakdown-table')).toBeNull();
    expect(fixture.componentInstance.selectedScenario()).toBeNull();
    expect(fixture.nativeElement.querySelector('.replay-error').textContent).toContain(state === 'error' ? 'calculation failed' : state === 'wrong-account' ? 'does not match' : 'incomplete holding coverage');
    fixture.nativeElement.querySelector('.replay-error button').click();
    http.expectOne('/api/v1/accounts/7/analytics/risk/replay/MARCH_2020').flush(replayFixture());
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.replay-error')).toBeNull();
    expect(fixture.nativeElement.querySelector('.breakdown-table')).toBeTruthy();
  });

  it('removes a previously supported replay if a fresh run loses full holding coverage', () => {
    loadRisk();
    fixture.nativeElement.querySelector('[aria-label="View breakdown MARCH_2020"]').click(); fixture.detectChanges();
    fixture.nativeElement.querySelector('[aria-label="Run replay MARCH_2020"]').click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.breakdown-table')).toBeNull();
    http.expectOne('/api/v1/accounts/7/analytics/risk/replay/MARCH_2020').flush({ ...replayFixture(), status: 'UNAVAILABLE', totalPnL: null });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Run replay MARCH_2020"]')).toBeNull();
    expect(fixture.componentInstance.selectedScenario()).toBeNull();
  });

  it.each(['EMPTY', 'UNAVAILABLE'] as const)('disables custom shocks for a known %s valuation without submitting a request', status => {
    const response = riskFixture();
    response.valuation.valuationStatus = status;
    if (status === 'EMPTY') response.valuation.holdings = [];
    loadRisk(response);
    const run = fixture.nativeElement.querySelector('.shock-card button.primary');
    expect(run.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('[aria-label="Market shock percentage"]').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.impact-panel').textContent).toContain('A current, supported holdings valuation is required');
    run.click();
    http.expectNone('/api/v1/accounts/7/analytics/risk/shock');
    expect(fixture.nativeElement.querySelector('.breakdown-table')).toBeNull();
  });

  it('rejects another account response and unsupported holding periods', () => {
    http.expectOne(url()).flush(riskFixture(8));
    expect(fixture.componentInstance.risk()).toBeNull();
    fixture.componentInstance.updateHorizon(30 as RiskHorizon);
    expect(fixture.componentInstance.horizon()).toBe(1);
    expect(fixture.componentInstance.error()).toContain('1 or 10');
  });

  it('renders short history and missing dates without invented observations or VaR', () => {
    const response = riskFixture();
    response.series = { status: 'UNAVAILABLE', wealth: [], returns: [], reasons: ['INCOMPLETE_HISTORY'] };
    response.valueAtRisk.forEach(v => { v.status = 'UNAVAILABLE'; v.lossAmount = v.lossPercent = null; v.reasons = ['INSUFFICIENT_HISTORY']; });
    response.metrics.maximumDrawdown.status = 'UNAVAILABLE';
    response.metrics.maximumDrawdown.value = null;
    response.metrics.maximumDrawdown.reasons = ['INCOMPLETE_HISTORY'];
    loadRisk(response);
    expect(fixture.nativeElement.textContent).toContain('INSUFFICIENT_HISTORY');
    expect(fixture.nativeElement.textContent).toContain('INCOMPLETE_HISTORY');
    expect(fixture.nativeElement.querySelector('.drawdown-chart')).toBeNull();
    expect(fixture.nativeElement.querySelector('.kpi-grid').textContent).not.toContain('$50.00');
  });

  it('runs the existing uniform shock on actual holdings and resets derived results on refresh', () => {
    loadRisk();
    const input = fixture.nativeElement.querySelector('[aria-label="Market shock percentage"]');
    input.value = '-10';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('.shock-card button.primary').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Calculating price shock');
    const request = http.expectOne('/api/v1/accounts/7/analytics/risk/shock');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ priceChangePercent: -10 });
    request.flush(shockFixture());
    fixture.detectChanges();
    expect(fixture.componentInstance.customResult()?.totalPnL).toBe(-100);
    expect(fixture.componentInstance.customResult()?.postShockEquity).toBe(1000);
    expect(fixture.componentInstance.waterfalls()).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('.breakdown-table').textContent).toContain('$540.00');
    fixture.componentInstance.loadDashboard();
    expect(fixture.componentInstance.customResult()).toBeNull();
    http.expectOne(url()).flush(riskFixture());
  });

  it.each(['unavailable', 'error', 'wrong-account'])('shows %s shock results without stale calculations', state => {
    loadRisk();
    const input = fixture.nativeElement.querySelector('[aria-label="Market shock percentage"]');
    input.value = '-10'; input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('.shock-card button.primary').click();
    const pending = http.expectOne('/api/v1/accounts/7/analytics/risk/shock');
    if (state === 'error') pending.flush({}, { status: 500, statusText: 'Calculation failed' });
    else {
      const response = shockFixture(state === 'wrong-account' ? 8 : 7);
      if (state === 'unavailable') { response.status = 'UNAVAILABLE'; response.reasons = ['STALE_PRICE']; response.totalPnL = null; }
      pending.flush(response);
    }
    fixture.detectChanges();
    expect(fixture.componentInstance.customResult()).toBeNull();
    expect(fixture.nativeElement.querySelector('.breakdown-table')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(state === 'unavailable' ? 'STALE_PRICE'
      : state === 'error' ? 'calculation failed' : 'does not match');
  });

  it('reset cancels a pending shock and clears results; invalid input never requests a calculation', () => {
    loadRisk();
    const input = fixture.nativeElement.querySelector('[aria-label="Market shock percentage"]');
    input.value = '-51'; input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('.shock-card button.primary').click();
    http.expectNone('/api/v1/accounts/7/analytics/risk/shock');
    input.value = '-10'; input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('.shock-card button.primary').click();
    const pending = http.expectOne('/api/v1/accounts/7/analytics/risk/shock');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.shock-card button').click();
    expect(pending.cancelled).toBe(true);
    fixture.detectChanges();
    expect(input.value).toBe('0');
    expect(fixture.componentInstance.customResult()).toBeNull();
  });

  it('keeps unavailable simulation results distinct from a calculated zero', () => {
    const response = riskFixture();
    response.monteCarlo = { ...response.monteCarlo!, status: 'UNAVAILABLE', reasons: ['INSUFFICIENT_HISTORY'],
      bins: [], varAmount: null, expectedShortfallAmount: null, expectedShortfallPercent: null };
    loadRisk(response);
    expect(fixture.nativeElement.querySelector('.histogram-chart')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Distribution and Expected Shortfall unavailable');
  });

  it('shows a real zero Expected Shortfall as available', () => {
    const response = riskFixture();
    response.monteCarlo = { ...response.monteCarlo!, varAmount: 0, expectedShortfallAmount: 0, expectedShortfallPercent: 0 };
    loadRisk(response);
    expect(fixture.nativeElement.querySelector('.es-callout').textContent).toContain('$0.00');
    expect(fixture.nativeElement.querySelector('.histogram-chart')).toBeTruthy();
  });
});
