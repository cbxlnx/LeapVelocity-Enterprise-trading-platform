import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { PerformancePanelComponent } from './performance-panel.component';
import { PortfolioPerformance } from '../../models/portfolio-performance.model';

const sample: PortfolioPerformance = {
  accountId: 1, period: '1Y', benchmark: 'SPY', simulationStatus: 'READY', weightingDate: '2026-10-13',
  requestedFrom: '2025-10-13', requestedTo: '2026-10-13', from: '2026-10-09', to: '2026-10-13',
  portfolioReturnPercent: 2, benchmarkReturnPercent: 5,
  initialWeights: [{ symbol: 'AAPL', weight: 0.6 }, { symbol: 'MSFT', weight: 0.4 }],
  points: [
    { date: '2026-10-09', portfolioIndex: 100, benchmarkIndex: 100 },
    { date: '2026-10-13', portfolioIndex: 102, benchmarkIndex: 105 },
  ], issues: [],
};

describe('Historical simulation panel', () => {
  function render(performance: PortfolioPerformance) {
    const fixture = TestBed.createComponent(PerformancePanelComponent);
    fixture.componentRef.setInput('performance', performance);
    fixture.detectChanges();
    return fixture;
  }

  it('shows both lines, actual range, initial weights and correctly scaled percentage returns', () => {
    const fixture = render(sample);
    const view = fixture.nativeElement;
    expect(view.querySelector('[data-testid="simulated-return"]').textContent).toBe('+2.00%');
    expect(view.querySelector('[data-testid="benchmark-return"]').textContent).toBe('+5.00%');
    expect(view.querySelectorAll('polyline').length).toBe(2);
    expect(view.querySelector('.effective-range').textContent).toContain('09 Oct 2026');
    expect(view.querySelector('.initial-weights').textContent).toContain('AAPL 60.0%');
    expect(view.textContent).toContain("does not represent your account's actual historical returns");
  });

  it('uses one y-axis for both series, including a flat or identical series', () => {
    for (const points of [sample.points, sample.points.map((point) => ({ ...point, portfolioIndex: 100, benchmarkIndex: 100 }))]) {
      const fixture = render({ ...sample, points });
      const portfolio = fixture.nativeElement.querySelector('.portfolio-line').getAttribute('points');
      const benchmark = fixture.nativeElement.querySelector('.benchmark-line').getAttribute('points');
      expect(portfolio).not.toMatch(/NaN|Infinity/);
      expect(benchmark).not.toMatch(/NaN|Infinity/);
      expect(portfolio.split(' ')[0]).toBe(benchmark.split(' ')[0]);
    }
  });

  it('renders empty and unavailable states without a fabricated zero return', () => {
    for (const simulationStatus of ['EMPTY', 'UNAVAILABLE'] as const) {
      const fixture = render({ ...sample, simulationStatus, points: [], from: null, to: null,
        portfolioReturnPercent: null, benchmarkReturnPercent: null, issues: ['Missing adjusted-price history for: SPY'] });
      expect(fixture.nativeElement.querySelector('svg')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="simulated-return"]')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain(simulationStatus === 'EMPTY' ? 'No invested holdings' : 'Missing adjusted-price history for: SPY');
    }
  });

  it('emits period changes and lets a failed request be retried', () => {
    const fixture = render(sample);
    const changed = vi.fn(), refreshed = vi.fn();
    fixture.componentInstance.periodChange.subscribe(changed);
    fixture.componentInstance.refresh.subscribe(refreshed);
    const select = fixture.nativeElement.querySelector('select');
    select.value = 'ALL';
    select.dispatchEvent(new Event('change'));
    expect(changed).toHaveBeenCalledWith('ALL');
    fixture.componentRef.setInput('error', 'Simulation request failed');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.simulation-error button').click();
    expect(refreshed).toHaveBeenCalledOnce();
  });
});
