import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Account, Position, AccountBalance, DashboardData } from '../shared/models/account.model';

@Injectable({
  providedIn: 'root',
})
export class MockAccountService {
  private mockAccount: Account = {
    id: 1,
    accountId: 'ACC-001',
    holderName: 'Alice Johnson',
    cashBalance: 250000.00,
    status: 'ACTIVE',
    version: 1,
    lastUpdated: new Date('2026-10-12T14:32:08Z'),
  };

  private mockPositions: Position[] = [
    {
      id: 1,
      accountId: 1,
      symbol: 'AAPL',
      quantity: 200,
      averageCost: 160.00,
      currentMarketValue: 36400.00,
      gainLoss: 2400.00,
      gainLossPercent: 7.05,
      quoteAvailable: true,
    },
    {
      id: 2,
      accountId: 1,
      symbol: 'MSFT',
      quantity: 300,
      averageCost: 420.00,
      currentMarketValue: 41900.00,
      gainLoss: -1000.00,
      gainLossPercent: -2.33,
      quoteAvailable: true,
    },
    {
      id: 3,
      accountId: 1,
      symbol: 'JPM',
      quantity: 150,
      averageCost: 200.00,
      currentMarketValue: 31500.00,
      gainLoss: 1500.00,
      gainLossPercent: 4.97,
      quoteAvailable: true,
    },
    {
      id: 4,
      accountId: 1,
      symbol: 'JNJ',
      quantity: 120,
      averageCost: 160.00,
      currentMarketValue: 16400.00,
      gainLoss: -600.00,
      gainLossPercent: -3.53,
      quoteAvailable: true,
    },
    {
      id: 5,
      accountId: 1,
      symbol: 'XOM',
      quantity: 250,
      averageCost: 110.00,
      currentMarketValue: 29000.00,
      gainLoss: 1500.00,
      gainLossPercent: 5.45,
      quoteAvailable: true,
    },
  ];

  private mockBalance: AccountBalance = {
    accountId: 1,
    cashBalance: 250000.00,
  };

  getDashboardData(accountId: number): Observable<DashboardData> {
    const totalMarketValue = this.mockPositions.reduce(
      (sum, p) => sum + (p.currentMarketValue || 0),
      0
    );
    const totalEquity = this.mockBalance.cashBalance + totalMarketValue;
    const unrealizedGainLoss = this.mockPositions.reduce(
      (sum, p) => sum + (p.gainLoss || 0),
      0
    );
    const unrealizedGainLossPercent =
      totalMarketValue > 0 ? (unrealizedGainLoss / totalMarketValue) * 100 : 0;

    return of({
      account: { ...this.mockAccount },
      balance: { ...this.mockBalance },
      positions: [...this.mockPositions],
      totalMarketValue,
      totalEquity,
      portfolioCostBasis: totalMarketValue - unrealizedGainLoss,
      unrealizedGainLoss,
      unrealizedGainLossPercent,
      unavailableQuoteCount: 0,
    });
  }
}