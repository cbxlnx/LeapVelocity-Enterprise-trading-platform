import { Injectable } from '@angular/core';
import { Observable, delay, of } from 'rxjs';
import { RISK_DASHBOARD_SEED } from '../data/risk-dashboard.mock';
import { RiskDashboardData } from '../models/risk-dashboard.model';

@Injectable({ providedIn: 'root' })
export class RiskDashboardMockService {
  getDashboard(): Observable<RiskDashboardData> {
    return of(cloneDashboard(RISK_DASHBOARD_SEED)).pipe(delay(40));
  }
}

function cloneDashboard(data: RiskDashboardData): RiskDashboardData {
  return {
    portfolios: data.portfolios.map((portfolio) => ({
      ...portfolio,
      oneDayVar: { ...portfolio.oneDayVar },
      holdings: portfolio.holdings.map((holding) => ({ ...holding })),
      distribution: portfolio.distribution.map((bin) => ({ ...bin })),
      drawdownSeries: portfolio.drawdownSeries.map((point) => ({ ...point })),
      insights: portfolio.insights.map((insight) => ({ ...insight })),
      sectorExposure: portfolio.sectorExposure.map((sector) => ({ ...sector, symbols: [...sector.symbols] })),
      scenarios: portfolio.scenarios.map((scenario) => ({
        ...scenario,
        waterfall: scenario.waterfall ? {
          ...scenario.waterfall,
          deltas: scenario.waterfall.deltas.map((delta) => ({ ...delta })),
        } : undefined,
      })),
      historicalWaterfalls: portfolio.historicalWaterfalls.map((waterfall) => ({
        ...waterfall,
        deltas: waterfall.deltas.map((delta) => ({ ...delta })),
      })),
    })),
  };
}
