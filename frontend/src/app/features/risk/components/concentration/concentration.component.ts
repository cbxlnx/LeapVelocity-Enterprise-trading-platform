import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ConcentrationView, RiskPortfolio } from '../../models/risk-dashboard.model';

@Component({
  selector: 'app-concentration',
  standalone: true,
  templateUrl: './concentration.component.html',
  styleUrl: './concentration.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConcentrationComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly viewChange = output<ConcentrationView>();
  readonly view = signal<ConcentrationView>('stock');
  readonly technologyShare = computed(() => this.portfolio().sectorExposure.find((sector) => sector.sector === 'Technology')?.allocationPercent ?? 0);
  readonly sectorScale = computed(() => {
    const largestExposure = Math.max(...this.portfolio().sectorExposure.map((sector) => sector.allocationPercent), 0);
    return Math.max(10, Math.ceil(largestExposure * 1.1));
  });
  readonly concentrationWatch = computed(() => {
    const current = this.portfolio();
    const largestSector = current.sectorExposure.reduce<(typeof current.sectorExposure)[number] | null>((largest, sector) => !largest || sector.allocationPercent > largest.allocationPercent ? sector : largest, null);
    const largestHolding = current.holdings.reduce<(typeof current.holdings)[number] | null>((largest, holding) => {
      const allocation = this.holdingAllocation(holding.marketValue, current.investedEquityUsd, holding.allocationPercent);
      const largestAllocation = largest ? this.holdingAllocation(largest.marketValue, current.investedEquityUsd, largest.allocationPercent) : -1;
      return !largest || allocation > largestAllocation ? holding : largest;
    }, null);
    if (!largestSector || !largestHolding) return 'No concentration data is available for this portfolio.';
    const sectorShare = largestSector.allocationPercent.toFixed(1);
    const holdingShare = this.holdingAllocation(largestHolding.marketValue, current.investedEquityUsd, largestHolding.allocationPercent).toFixed(1);
    const sectorDescription = largestSector.allocationPercent >= 50 ? `over half of invested equity is in ${largestSector.sector}` : `the largest sector is ${largestSector.sector} at ${sectorShare}% of invested equity`;
    return `Concentration watch: ${sectorDescription}; ${largestHolding.symbol} represents ${holdingShare}% of invested equity. A high cash balance does not diversify the stock sleeve.`;
  });

  selectView(view: ConcentrationView): void {
    this.view.set(view);
    this.viewChange.emit(view);
  }

  formatMetric(value: number | null, digits = 1): string {
    return value === null ? 'Unavailable' : value.toFixed(digits);
  }

  formatCurrency(value: number | null): string {
    return value === null ? 'Unavailable' : value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  private holdingAllocation(marketValue: number | null, investedEquityUsd: number, allocationPercent: number | null): number {
    return allocationPercent ?? (marketValue === null || investedEquityUsd === 0 ? 0 : marketValue / investedEquityUsd * 100);
  }
}
