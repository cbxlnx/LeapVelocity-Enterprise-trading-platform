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

  selectView(view: ConcentrationView): void {
    this.view.set(view);
    this.viewChange.emit(view);
  }
}
