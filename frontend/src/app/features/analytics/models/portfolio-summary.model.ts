export interface PortfolioHolding {
  symbol: string;
  currency: string | null;
  quantity: number;
  averageCost: number;
  price: number | null;
  marketValue: number | null;
  costBasis: number | null;
  unrealizedPnL: number | null;
  /** Fraction of invested holdings, excluding cash. */
  weight: number | null;
}

export interface PortfolioSummary {
  accountId: number;
  currency: string;
  asOf: string | null;
  valuationStatus: 'READY' | 'EMPTY' | 'UNAVAILABLE';
  holdingsValue: number | null;
  cashBalance: number;
  totalEquity: number | null;
  costBasis: number | null;
  unrealizedPnL: number | null;
  holdings: PortfolioHolding[];
  issues: string[];
}
