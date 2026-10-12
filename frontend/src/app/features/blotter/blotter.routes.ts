import { Routes } from '@angular/router';
import { BlotterComponent } from './blotter/blotter.component';

export const BLOTTER_ROUTES: Routes = [
  {
    path: '',
    children: [
      {
        path: '',
        component: BlotterComponent,
      },
    ],
  },
];
