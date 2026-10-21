import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, MarkPointComponent, TooltipComponent } from 'echarts/components';
import { SVGRenderer } from 'echarts/renderers';
import { RiskDashboardComponent } from './risk-dashboard.component';
import { AuthService } from '../../../../services/auth.service';
import { riskFixture, TEST_INSTRUMENTS } from '../../testing/risk-dashboard.fixture';
import { RiskHorizon } from '../../models/risk-dashboard.model';

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, MarkPointComponent, SVGRenderer]);
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

  it('connects KPIs, VaR, drawdown, insights and holdings to one authenticated snapshot', () => {
    http.expectOne(url()).flush(riskFixture());
    fixture.detectChanges();
    const summary = fixture.nativeElement.querySelector('.kpi-grid').textContent;
    for (const value of ['$1,100.00', '$50.00', '$60.00', '$55.00', '15.25%', '0.80', '−20.00%']) expect(summary).toContain(value);
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Synthetic Alpha');
    expect(text).toContain('AAA · 60.00%');
    expect(text).not.toContain('MSFT');
    expect(text).not.toContain('44,935');
    expect(text).toContain('Historical replay unavailable');
    expect(text).toContain('Volatility contribution unavailable');
    expect(text).toContain('Expected Shortfall is not implemented');
    expect(fixture.nativeElement.querySelector('.drawdown-chart')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.histogram-chart')).toBeNull();
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
    http.expectOne(url()).flush(response);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('INSUFFICIENT_HISTORY');
    expect(fixture.nativeElement.textContent).toContain('INCOMPLETE_HISTORY');
    expect(fixture.nativeElement.querySelector('.drawdown-chart')).toBeNull();
    expect(fixture.nativeElement.querySelector('.kpi-grid').textContent).not.toContain('$50.00');
  });

  it('runs the existing uniform shock on actual holdings and resets derived results on refresh', () => {
    http.expectOne(url()).flush(riskFixture());
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('[aria-label="Market shock percentage"]');
    input.value = '-10';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('.shock-card button.primary').click();
    fixture.detectChanges();
    expect(fixture.componentInstance.customResult()?.lossUsd).toBe(100);
    expect(fixture.componentInstance.customResult()?.postShockEquityUsd).toBe(1000);
    expect(fixture.componentInstance.waterfalls()).toHaveLength(1);
    fixture.componentInstance.loadDashboard();
    expect(fixture.componentInstance.customResult()).toBeNull();
    http.expectOne(url()).flush(riskFixture());
  });
});
