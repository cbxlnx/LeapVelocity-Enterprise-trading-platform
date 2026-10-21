import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { PortfolioRisk } from '../../../analytics/models/portfolio-risk.model';
import { RiskHorizon } from '../../models/risk-dashboard.model';
import { createVarChartOptions } from '../../utils/risk-chart-options';
import { formatAmount } from '../../utils/risk-calculations';

@Component({ selector: 'app-var-models', standalone: true, imports: [NgxEchartsDirective],
  templateUrl: './var-models.component.html', styleUrl: './var-models.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class VarModelsComponent {
  readonly assessment = input.required<PortfolioRisk>();
  readonly horizon = input.required<RiskHorizon>();
  readonly horizonChange = output<RiskHorizon>();
  readonly options = computed(() => createVarChartOptions(this.assessment()));
  readonly available = computed(() => this.assessment().valueAtRisk.some(value => value.status === 'READY'));
  readonly format = formatAmount;
  readonly horizons: Array<{ value: RiskHorizon; label: string }> = [{ value: 1, label: '1 day' }, { value: 10, label: '10 days' }];
  selectHorizon(horizon: RiskHorizon): void { this.horizonChange.emit(horizon); }
}
