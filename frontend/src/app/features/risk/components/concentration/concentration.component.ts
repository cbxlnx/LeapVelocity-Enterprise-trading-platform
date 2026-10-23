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
}
