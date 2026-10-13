import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { OrderService } from './order.service';

describe('OrderService', () => {
  let service: OrderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [OrderService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderService);
    http = TestBed.inject(HttpTestingController);
  });

  it('places an order and maps the backend order id', async () => {
    const request = {
      accountId: 1,
      symbol: 'AAPL',
      side: 'BUY' as const,
      quantity: 2,
      price: 150,
      idempotencyKey: 'order-1',
    };
    const result = firstValueFrom(service.placeOrder(request));
    const testRequest = http.expectOne('http://localhost:8081/api/v1/orders');

    expect(testRequest.request.method).toBe('POST');
    expect(testRequest.request.body).toEqual(request);
    testRequest.flush({
      id: 'order-1',
      status: 'FILLED',
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 2,
      price: 150,
    });

    await expect(result).resolves.toEqual({
      orderId: 'order-1',
      status: 'FILLED',
      message: 'Order filled.',
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 2,
      price: 150,
    });
    http.verify();
  });

  it('turns a server error into a meaningful error', async () => {
    const result = firstValueFrom(
      service.cancelOrder('order-1')
    );
    http.expectOne('http://localhost:8081/api/v1/orders/order-1').flush(
      {},
      { status: 500, statusText: 'Server Error' }
    );

    await expect(result).rejects.toThrow(
      'Unable to cancel the order. The server returned an error (500).'
    );
    http.verify();
  });
});