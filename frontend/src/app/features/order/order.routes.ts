import { Routes } from '@angular/router';
import { OrderTicketComponent } from './order-ticket/order-ticket.component';

export const ORDER_ROUTES: Routes = [
  {
    path: '',
    children: [
      {
        path: '',
        component: OrderTicketComponent,
      },
    ],
  },
];
