export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED' | string;
export type OrderSide = 'BUY' | 'SELL';
export type OrderStatus = 'NEW' | 'FILLED' | 'REJECTED' | 'CANCELLED' | string;

export interface Account {
  id: number;
  accountId: string;
  holderName: string;
  cashBalance: number;
  status: AccountStatus;
  version: number;
}

export interface Balance {
  accountId: number;
  cashBalance: number;
}

export interface DepositCashRequest {
  amount: number;
}

export interface Position {
  accountId: number;
  symbol: string;
  quantity: number;
  averageCost: number;
  marketValue?: number;
}

export interface Instrument {
  id: number;
  symbol: string;
  name: string;
  assetClass: string;
  currency: string;
  tradable: boolean;
}

export interface Order {
  id: string;
  accountId: number;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  status: OrderStatus;
  createdOn: string;
}

export interface PlaceOrderRequest {
  accountId: number;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  idempotencyKey: string;
}

export interface OrderResponse {
  orderId: string;
  status: OrderStatus;
  message: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
}

export interface QuoteSnapshot {
  symbol: string;
  lastPrice: number;
  bid: number | null;
  ask: number | null;
  currency: string;
  asOf: string;
  source: string;
  change?: number | null;
  changePercent?: number | null;
  dayHigh?: number | null;
  dayLow?: number | null;
  volume?: number | null;
}

export interface BatchQuotesResponse {
  quotes: QuoteSnapshot[];
  missingSymbols: string[];
  serverTimestamp: string;
}