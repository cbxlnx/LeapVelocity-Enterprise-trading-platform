import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { EChartsOption } from 'echarts';
import { RiskHorizon, RiskPortfolio } from '../../models/risk-dashboard.model';
import { createVarChartOptions } from '../../utils/risk-chart-options';

@Component({
  selector: 'app-var-models',
  standalone: true,
  imports: [NgxEchartsDirective],
  templateUrl: './var-models.component.html',
  styleUrl: './var-models.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VarModelsComponent {
  readonly portfolio = input.required<RiskPortfolio>();
  readonly horizon = input.required<RiskHorizon>();
  readonly horizonChange = output<RiskHorizon>();
  readonly options = computed<EChartsOption>(() => createVarChartOptions(this.portfolio(), this.horizon()));
  readonly horizons: Array<{ value: RiskHorizon; label: string }> = [
    { value: 1, label: '1 day' },
    { value: 10, label: '10 days' },
    { value: 30, label: '30 days' },
  ];

  selectHorizon(horizon: RiskHorizon): void {
    this.horizonChange.emit(horizon);
  }
}
