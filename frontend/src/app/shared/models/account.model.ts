export interface Account {
  id: number;
  accountId: string;
  holderName: string;
  cashBalance: number;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED' | string;
  version: number;
  lastUpdated: Date;
}

export interface Position {
  id: number;
  accountId: number;
  symbol: string;
  quantity: number;
  averageCost: number;
  currentMarketValue?: number;
  gainLoss?: number;
  gainLossPercent?: number;
}

export interface AccountBalance {
  accountId: number;
  cashBalance: number;
}

export interface DashboardData {
  account: Account;
  balance: AccountBalance;
  positions: Position[];
  totalMarketValue: number;
  totalEquity: number;
  unrealizedGainLoss: number;
  unrealizedGainLossPercent: number;
}