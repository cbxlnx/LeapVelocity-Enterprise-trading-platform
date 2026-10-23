import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { EChartsOption } from 'echarts';
import { ScenarioWaterfall } from '../../models/risk-dashboard.model';
import { createWaterfallOptions } from '../../utils/risk-chart-options';

@Component({
  selector: 'app-scenario-waterfall',
  standalone: true,
  imports: [NgxEchartsDirective],
  templateUrl: './scenario-waterfall.component.html',
  styleUrl: './scenario-waterfall.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScenarioWaterfallComponent {
  readonly title = input('Historical scenario breakdown');
  readonly emptyMessage = input('No waterfall is available.');
  readonly waterfalls = input.required<ScenarioWaterfall[]>();
  readonly anchorIds = input<string[]>([]);
  readonly options = computed(() => this.waterfalls().map((waterfall) => createWaterfallOptions(waterfall)));
  optionFor(index: number): EChartsOption {
    return this.options()[index];
  }
}
