import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { EChartsOption } from 'echarts';
import { RiskPortfolio } from '../../models/risk-dashboard.model';
import { createContributionOptions } from '../../utils/risk-chart-options';
import { calculateRiskContributionTotal } from '../../utils/risk-calculations';

@Component({
  selector: 'app-risk-contribution',
  standalone: true,
  imports: [NgxEchartsDirective],
  templateUrl: './risk-contribution.component.html',
  styleUrl: './risk-contribution.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskContributionComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly options = computed<EChartsOption>(() => createContributionOptions(this.portfolio()));
  readonly contributionTotal = computed(() => calculateRiskContributionTotal(this.portfolio().holdings.map((holding) => holding.volatilityContributionPercent)));
  readonly leadingSector = computed(() => {
    const bySector = new Map<string, { contribution: number; allocation: number; symbols: string[] }>();
    for (const holding of this.portfolio().holdings) {
      if (!holding.sector) continue;
      const current = bySector.get(holding.sector) ?? { contribution: 0, allocation: 0, symbols: [] };
      current.contribution += holding.volatilityContributionPercent ?? 0;
      current.allocation += holding.allocationPercent ?? 0;
      current.symbols.push(holding.symbol);
      bySector.set(holding.sector, current);
    }
    return [...bySector.entries()].sort(([, left], [, right]) => right.contribution - left.contribution)[0] ?? null;
  });

  cashShare(): string {
    const portfolio = this.portfolio();
    return portfolio.portfolioValueUsd === null || portfolio.portfolioValueUsd === 0 ? 'Unavailable' : (portfolio.cashReserveUsd / portfolio.portfolioValueUsd * 100).toFixed(2);
  }

  metric(value: number | null, digits = 2): string {
    return value === null ? 'Unavailable' : value.toFixed(digits);
  }
}
