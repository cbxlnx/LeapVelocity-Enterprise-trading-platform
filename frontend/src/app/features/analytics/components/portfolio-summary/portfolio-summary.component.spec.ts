import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { PortfolioSummaryComponent } from './portfolio-summary.component';
import { PortfolioSummary } from '../../models/portfolio-summary.model';

const summary: PortfolioSummary = {
  accountId: 1, currency: 'USD', asOf: '2025-10-01', valuationStatus: 'READY',
  holdingsValue: 24, cashBalance: 5, totalEquity: 29, costBasis: 20, unrealizedPnL: 4,
  holdings: [{ symbol: 'AAPL', currency: 'USD', quantity: 2, averageCost: 10,
    price: 12, marketValue: 24, costBasis: 20, unrealizedPnL: 4, weight: 1 }], issues: [],
};

describe('Portfolio summary', () => {
  function render(value: PortfolioSummary) {
    const fixture = TestBed.createComponent(PortfolioSummaryComponent);
    fixture.componentRef.setInput('summary', value);
    fixture.detectChanges();
    return fixture;
  }

  it('shows current holdings, cash, P&L and an allocation excluding cash', () => {
    const fixture = render(summary);
    const view = fixture.nativeElement;
    expect(view.querySelector('[data-testid="holdings-value"]').textContent).toBe('$24.00');
    expect(view.querySelector('[data-testid="cash-balance"]').textContent).toBe('$5.00');
    expect(view.querySelector('[data-testid="total-equity"]').textContent).toBe('$29.00');
    expect(view.querySelector('[data-testid="unrealized-pnl"]').textContent).toBe('$4.00');
    expect(view.querySelector('time').getAttribute('datetime')).toBe('2025-10-01');
    expect(view.querySelectorAll('tbody tr').length).toBe(1);
    expect(view.querySelector('.allocation-label').textContent).toContain('100.0%');
    expect(view.textContent).toContain('Demo accounts use illustrative purchase costs');
  });

  it('keeps cash and holdings visible but does not invent missing valuations', () => {
    const fixture = render({ ...summary, valuationStatus: 'UNAVAILABLE', asOf: null,
      holdingsValue: null, totalEquity: null, unrealizedPnL: null,
      holdings: [{ ...summary.holdings[0], price: null, marketValue: null, unrealizedPnL: null, weight: null }],
      issues: ['Missing completed price history for: AAPL'] });
    const view = fixture.nativeElement;
    expect(view.querySelector('[role="alert"]').textContent).toContain('Missing completed price history for: AAPL');
    expect(view.querySelector('[data-testid="total-equity"]').textContent).toBe('Unavailable');
    expect(view.querySelector('[data-testid="holdings-value"]').textContent).toBe('Unavailable');
    expect(view.querySelector('[data-testid="cash-balance"]').textContent).toBe('$5.00');
    expect(view.querySelector('tbody').textContent).toContain('AAPL');
    expect(view.querySelectorAll('.allocation-fill').length).toBe(0);
  });

  it('shows a cash-only account without an allocation or invented asOf', () => {
    const fixture = render({ ...summary, valuationStatus: 'EMPTY', asOf: null,
      holdingsValue: 0, totalEquity: 5, costBasis: 0, unrealizedPnL: 0, holdings: [] });
    const view = fixture.nativeElement;
    expect(view.querySelector('[data-testid="total-equity"]').textContent).toBe('$5.00');
    expect(view.textContent).toContain('No invested holdings');
    expect(view.querySelector('time')).toBeNull();
    expect(view.querySelector('table')).toBeNull();
  });

  it('shows loading and lets the user retry a failed summary', () => {
    const fixture = TestBed.createComponent(PortfolioSummaryComponent);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading portfolio summary');
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('error', 'Unable to load portfolio summary.');
    const retry = vi.fn();
    fixture.componentInstance.refresh.subscribe(retry);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.summary-error button').click();
    expect(retry).toHaveBeenCalledOnce();
  });
});
