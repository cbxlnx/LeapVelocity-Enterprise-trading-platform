export interface HistoricalPricePoint {
  date: string;
  close: number;
  adjustedClose: number;
  volume: number;
  source: string;
}

export interface HistoricalPrices {
  symbol: string;
  currency: string;
  from: string;
  to: string;
  prices: HistoricalPricePoint[];
}
