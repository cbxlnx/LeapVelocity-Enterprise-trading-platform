import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, MarkAreaComponent, MarkLineComponent, TooltipComponent } from 'echarts/components';
import { SVGRenderer } from 'echarts/renderers';
import { RiskDashboardComponent } from './risk-dashboard.component';
import { RISK_DASHBOARD_SEED } from '../../data/risk-dashboard.mock';
import { RiskDashboardApiService } from '../../services/risk-dashboard-api.service';

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, MarkAreaComponent, MarkLineComponent, SVGRenderer]);

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class { observe(): void {} unobserve(): void {} disconnect(): void {} } as typeof ResizeObserver;
}

describe('Risk dashboard', () => {
  let fixture: ComponentFixture<RiskDashboardComponent>;
  let getDashboard: ReturnType<typeof vi.fn>;
  let runCustomShock: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    getDashboard = vi.fn(() => of(RISK_DASHBOARD_SEED));
    runCustomShock = vi.fn(() => of({ lossUsd: 100, impactPercent: 1, postShockEquityUsd: 9900 }));
    await TestBed.configureTestingModule({
      imports: [RiskDashboardComponent],
      providers: [
        provideRouter([]),
        provideEchartsCore({ echarts }),
        {
          provide: RiskDashboardApiService,
          useValue: {
            getDashboard,
            runCustomShock,
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RiskDashboardComponent);
    fixture.detectChanges();
  });

  it('loads the dashboard sections and reference account metrics', async () => {
    await new Promise((resolve) => setTimeout(resolve, 60));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Risk dashboard');
    expect(fixture.nativeElement.textContent).toContain('Portfolio value');
    expect(fixture.nativeElement.textContent).toContain('Stress test scenarios');
    expect(fixture.nativeElement.querySelector('.risk-card')).toBeTruthy();
  });

  it('changes VaR horizon and concentration view without mutating seed data', async () => {
    await new Promise((resolve) => setTimeout(resolve, 60));
    fixture.detectChanges();
    fixture.componentInstance.updateHorizon(30);
    expect(fixture.componentInstance.horizon()).toBe(30);
    const sectorButton = fixture.nativeElement.querySelector('.concentration-card .segmented button:last-child');
    sectorButton.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sector');
  });

  it('reloads the server data for a selected lookback without an account selector', async () => {
    await new Promise((resolve) => setTimeout(resolve, 60));
    fixture.componentInstance.setHistoricalWindow('5Y');
    fixture.detectChanges();

    expect(getDashboard).toHaveBeenLastCalledWith(1260);
    expect(fixture.nativeElement.textContent).not.toContain('Account / portfolio');
  });

  it('commits custom shock output locally without calling the legacy endpoint', async () => {
    await new Promise((resolve) => setTimeout(resolve, 60));
    fixture.componentInstance.runCustomShock({ market: -10, Technology: 0, Financials: 0, Energy: 0, Healthcare: 0 });

    expect(fixture.componentInstance.customResult()?.waterfall?.title).toBe('Custom scenario');
    expect(runCustomShock).not.toHaveBeenCalled();
  });
});
