import { Routes } from '@angular/router';
import { provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import {
  AriaComponent,
  DataZoomComponent,
  DatasetComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
  TransformComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([
  BarChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  DatasetComponent,
  TransformComponent,
  DataZoomComponent,
  MarkLineComponent,
  MarkAreaComponent,
  TitleComponent,
  AriaComponent,
  CanvasRenderer,
]);

export const RISK_ROUTES: Routes = [
  {
    path: '',
    title: 'Risk Dashboard | TradeVelocity',
    providers: [provideEchartsCore({ echarts })],
    loadComponent: () =>
      import('./pages/risk-dashboard/risk-dashboard.component').then(
        (m) => m.RiskDashboardComponent,
      ),
  },
];
