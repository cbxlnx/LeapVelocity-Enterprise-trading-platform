import { provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer, SVGRenderer } from 'echarts/renderers';
import { Routes } from '@angular/router';

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer, SVGRenderer]);

export const ANALYTICS_ROUTES: Routes = [
  {
    path: '',
    title: 'Analytics | TradeVelocity',
    providers: [provideEchartsCore({ echarts })],
    loadComponent: () =>
      import('./pages/analytics-dashboard/analytics-dashboard.component').then(
        (m) => m.AnalyticsDashboardComponent,
      ),
  },
];
