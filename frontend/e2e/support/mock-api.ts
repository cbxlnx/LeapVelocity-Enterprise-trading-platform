import { Page } from '@playwright/test';

export const ACCESS_TOKEN = [
  'e30.eyJ1c2VybmFtZSI6ImpvaG4iLCJhY2NvdW50SWQiOjcsImV4cCI6MjAwMDAwMDAwMH0',
  'test-signature',
].join('.');

const account = {
  id: 7,
  accountId: 'ACC-007',
  holderName: 'john',
  cashBalance: 100000,
  status: 'ACTIVE',
  version: 0,
};

const positions = [
  { accountId: 7, symbol: 'AAPL', quantity: 10, averageCost: 90, marketValue: 1000 },
];

const instruments = [
  { id: 1, symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { id: 2, symbol: 'MSFT', name: 'Microsoft Corp.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
];

const initialOrders = [
  { id: 'order-new', accountId: 7, symbol: 'AAPL', side: 'BUY', quantity: 2, price: 98, status: 'NEW', createdOn: '2026-10-14T12:00:00Z' },
  { id: 'order-filled', accountId: 7, symbol: 'MSFT', side: 'SELL', quantity: 1, price: 300, status: 'FILLED', createdOn: '2026-10-13T12:00:00Z' },
];

export async function mockTradingApi(page: Page): Promise<void> {
  let orders = [...initialOrders];

  await page.route('**/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: ACCESS_TOKEN, refreshToken: 'refresh-token', expiresIn: 3600 }),
    });
  });

  await page.route('**/auth/refresh', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: ACCESS_TOKEN }) });
  });

  await page.route('**/api/v1/instruments', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(instruments) });
  });

  await page.route('**/api/v1/accounts/7', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(account) });
  });

  await page.route('**/api/v1/accounts/7/balance', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accountId: 7, cashBalance: 100000 }) });
  });

  await page.route('**/api/v1/accounts/7/positions', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(positions) });
  });

  await page.route('**/api/v1/accounts/7/orders', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(orders) });
  });

  await page.route('**/api/v1/orders', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue();
      return;
    }

    const request = route.request().postDataJSON() as {
      symbol: string;
      side: 'BUY' | 'SELL';
      quantity: number;
      price: number;
    };
    const orderId = `order-${request.side.toLowerCase()}-${orders.length + 1}`;
    orders = [{
      id: orderId,
      accountId: 7,
      symbol: request.symbol,
      side: request.side,
      quantity: request.quantity,
      price: request.price,
      status: 'FILLED',
      createdOn: '2026-10-15T12:00:00Z',
    }, ...orders];

    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ id: orderId, status: 'FILLED', symbol: request.symbol, side: request.side, quantity: request.quantity, price: request.price }),
    });
  });
}

export async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Username').fill('john');
  await page.getByLabel('Password').fill('password');
  await page.getByRole('button', { name: 'Login' }).click();
  await page.waitForURL('**/dashboard');
}