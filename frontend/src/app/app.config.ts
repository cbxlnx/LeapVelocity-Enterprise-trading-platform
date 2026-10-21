import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideEchartsCore({ echarts }),
    provideAnimations(), // Required for Angular Material
    provideRouter(
      routes,
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
    ),
  ],
};