import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { ScenarioWaterfall, ShockResult } from '../../models/risk-dashboard.model';
import { createWaterfallOptions } from '../../utils/risk-chart-options';
import { formatAmount } from '../../utils/risk-calculations';
@Component({ selector: 'app-scenario-waterfall', standalone: true, imports: [NgxEchartsDirective],
  templateUrl: './scenario-waterfall.component.html', styleUrl: './scenario-waterfall.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class ScenarioWaterfallComponent {
  readonly waterfalls = input.required<ScenarioWaterfall[]>();
  readonly result = input<ShockResult | null>(null);
  readonly options = computed(() => this.waterfalls().map(waterfall => createWaterfallOptions(waterfall)));
  readonly format = formatAmount;
}
