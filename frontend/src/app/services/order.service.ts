import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { OrderResponse, PlaceOrderRequest } from '../models/api.models';
import { handleApiError } from './api-error';

interface OrderApiResponse {
  id?: string;
  orderId?: string;
  status: OrderResponse['status'];
  message?: string;
  symbol: string;
  side: OrderResponse['side'];
  quantity: number;
  price: number;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly ordersUrl = `${environment.apiUrl}/v1/orders`;

  constructor(private readonly http: HttpClient) {}

  placeOrder(request: PlaceOrderRequest): Observable<OrderResponse> {
    return this.http.post<OrderApiResponse>(this.ordersUrl, request).pipe(
      map((response) => this.toOrderResponse(response)),
      catchError(handleApiError('place the order'))
    );
  }

  cancelOrder(orderId: string): Observable<OrderResponse> {
    return this.http.delete<OrderApiResponse>(`${this.ordersUrl}/${orderId}`).pipe(
      map((response) => this.toOrderResponse(response)),
      catchError(handleApiError('cancel the order'))
    );
  }

  private toOrderResponse(response: OrderApiResponse): OrderResponse {
    return {
      orderId: response.orderId ?? response.id ?? '',
      status: response.status,
      message: response.message ?? `Order ${response.status.toLowerCase()}.`,
      symbol: response.symbol,
      side: response.side,
      quantity: response.quantity,
      price: response.price,
    };
  }
}