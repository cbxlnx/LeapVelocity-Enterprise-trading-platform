import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PriceChartComponent } from './price-chart.component';
import { defaultHistoryRange, formatTradingDate } from '../../models/trading-date';

describe('Price chart and trading dates', () => {
  it('renders a flat series and a single observation without invalid coordinates', () => {
    const fixture = TestBed.createComponent(PriceChartComponent);
    const point = { date: '2025-10-01', close: 250, adjustedClose: 248, volume: 0, source: 'yfinance' };
    for (const prices of [[point], [point, { ...point, date: '2025-10-02' }]]) {
      fixture.componentRef.setInput('prices', prices);
      fixture.detectChanges();
      const coordinates = fixture.nativeElement.querySelector('polyline').getAttribute('points');
      expect(coordinates).not.toMatch(/NaN|Infinity/);
      expect(fixture.nativeElement.querySelectorAll('.price-point').length).toBe(prices.length);
    }
  });

  it('uses New York calendar dates and clamps leap-year ranges', () => {
    expect(defaultHistoryRange(new Date('2026-10-14T02:00:00Z')))
      .toEqual({ from: '2025-10-12', to: '2026-10-12' });
    expect(defaultHistoryRange(new Date('2024-03-01T12:00:00Z')))
      .toEqual({ from: '2023-02-28', to: '2024-02-29' });
    expect(formatTradingDate('2025-10-01')).toBe('01 Oct 2025');
  });
});
