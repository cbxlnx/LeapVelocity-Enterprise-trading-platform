import { test, expect, Page } from '@playwright/test';

const fixture = {
  symbol: 'AAPL', currency: 'USD', from: '2025-10-01', to: '2025-10-02',
  prices: [
    { date: '2025-10-01', close: 250, adjustedClose: 248, volume: 100, source: 'test-fixture' },
    { date: '2025-10-02', close: 253, adjustedClose: 251, volume: 200, source: 'test-fixture' },
  ],
};

const instrumentFixtures = [
  ['AAPL', 'Apple Inc.'], ['MSFT', 'Microsoft Corporation'], ['GOOGL', 'Alphabet Inc. Class A'],
  ['AMZN', 'Amazon.com, Inc.'], ['NVDA', 'NVIDIA Corporation'], ['TSLA', 'Tesla, Inc.'],
  ['JPM', 'JPMorgan Chase & Co.'], ['JNJ', 'Johnson & Johnson'], ['XOM', 'Exxon Mobil Corporation'],
  ['WMT', 'Walmart Inc.'], ['SPY', 'SPDR S&P 500 ETF Trust'], ['QQQ', 'Invesco QQQ Trust'],
].map(([symbol, name], id) => ({ id, symbol, name, currency: 'USD', tradable: true,
  assetClass: ['SPY', 'QQQ'].includes(symbol) ? 'FUND' : 'EQUITY' }));

async function signInForMockRequests(page: Page) {
  // Browser-only fixture. These tests intercept history requests and do not
  // claim to authenticate against a real backend.
  await page.addInitScript(() => {
    const payload = btoa(JSON.stringify({ username: 'Analytics test', accountId: 1 }));
    localStorage.setItem('leap_access_token', `fixture.${payload}.fixture`);
  });
  await page.route('**/api/v1/instruments', route => route.fulfill({ json: instrumentFixtures }));
  await page.route('**/api/v1/accounts/1/analytics/summary', (route) => route.fulfill({ json: {
    accountId: 1, currency: 'USD', asOf: null, valuationStatus: 'EMPTY',
    holdingsValue: 0, cashBalance: 5, totalEquity: 5, costBasis: 0, unrealizedPnL: 0, holdings: [], issues: [],
  } }));
  await page.route('**/api/v1/accounts/1/analytics/performance?**', (route) => route.fulfill({ json: {
    accountId: 1, period: '1Y', benchmark: 'SPY', simulationStatus: 'EMPTY', weightingDate: null,
    requestedFrom: null, requestedTo: null, from: null, to: null, portfolioReturnPercent: null,
    benchmarkReturnPercent: null, initialWeights: [], points: [], issues: [],
  } }));
}

const performanceFixture = {
  accountId: 1, period: '1Y', benchmark: 'SPY', simulationStatus: 'READY', weightingDate: '2026-10-13',
  requestedFrom: '2025-10-13', requestedTo: '2026-10-13', from: '2026-10-09', to: '2026-10-13',
  portfolioReturnPercent: 2, benchmarkReturnPercent: 5,
  initialWeights: [{ symbol: 'AAPL', weight: 0.6 }, { symbol: 'MSFT', weight: 0.4 }],
  points: [
    { date: '2026-10-09', portfolioIndex: 100, benchmarkIndex: 100 },
    { date: '2026-10-13', portfolioIndex: 102, benchmarkIndex: 105 },
  ], issues: [],
};

test('compares two indexed series and reloads all supported simulation periods', async ({ page }) => {
  await signInForMockRequests(page);
  const selected: string[] = [];
  await page.route('**/api/v1/accounts/1/analytics/performance?**', (route) => {
    const period = new URL(route.request().url()).searchParams.get('period')!;
    selected.push(period);
    return route.fulfill({ json: { ...performanceFixture, period } });
  });
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => route.fulfill({ json: fixture }));
  await page.goto('/analytics');
  await expect(page.getByTestId('simulated-return')).toHaveText('+2.00%');
  await expect(page.getByTestId('benchmark-return')).toHaveText('+5.00%');
  await expect(page.locator('app-performance-chart polyline')).toHaveCount(2);
  await expect(page.getByRole('img', { name: 'Hypothetical portfolio and SPY indices, starting at 100' })).toBeVisible();
  for (const period of ['1M', '3M', '6M', '2Y', '1Y']) {
    await page.getByLabel('Simulation period').selectOption(period);
    await expect(page.getByTestId('simulated-return')).toBeVisible();
    expect(selected.at(-1)).toBe(period);
  }
  await expect(page.locator('app-performance-panel')).toContainText("does not represent your account's actual historical returns");
});

test('reports missing benchmark data and retries failed simulation requests', async ({ page }) => {
  await signInForMockRequests(page);
  let calls = 0;
  await page.route('**/api/v1/accounts/1/analytics/performance?**', (route) => {
    calls += 1;
    if (calls === 1) return route.fulfill({ status: 500, json: { message: 'Simulation server unavailable' } });
    return route.fulfill({ json: { ...performanceFixture, simulationStatus: 'UNAVAILABLE', points: [],
      portfolioReturnPercent: null, benchmarkReturnPercent: null, from: null, to: null,
      issues: ['Missing adjusted-price history for: SPY'] } });
  });
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => route.fulfill({ json: fixture }));
  await page.goto('/analytics');
  await expect(page.locator('app-performance-panel [role="alert"]')).toContainText('Simulation server unavailable');
  await page.getByRole('button', { name: 'Retry simulation', exact: true }).click();
  await expect(page.locator('app-performance-panel [role="alert"]')).toContainText('Missing adjusted-price history for: SPY');
  await expect(page.locator('app-performance-chart')).toHaveCount(0);
  await expect(page.locator('app-price-chart polyline')).toBeVisible();
});

const portfolioFixture = {
  accountId: 1, currency: 'USD', asOf: '2025-10-01', valuationStatus: 'READY',
  holdingsValue: 24, cashBalance: 5, totalEquity: 29, costBasis: 20, unrealizedPnL: 4,
  holdings: [{ symbol: 'AAPL', currency: 'USD', quantity: 2, averageCost: 10, price: 12,
    marketValue: 24, costBasis: 20, unrealizedPnL: 4, weight: 1 }], issues: [],
};

test('shows portfolio KPI, holdings and allocation independently from the price chart', async ({ page }) => {
  await signInForMockRequests(page);
  await page.route('**/api/v1/accounts/1/analytics/summary', (route) => route.fulfill({ json: portfolioFixture }));
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => route.fulfill({ json: fixture }));
  await page.goto('/analytics');
  await expect(page.getByTestId('total-equity')).toHaveText('$29.00');
  await expect(page.getByTestId('cash-balance')).toHaveText('$5.00');
  await expect(page.locator('.allocation-label')).toContainText('100.0%');
  await expect(page.locator('app-portfolio-summary tbody tr')).toHaveCount(1);
  await expect(page.locator('app-portfolio-summary time')).toHaveAttribute('datetime', '2025-10-01');
  await expect(page.locator('polyline')).toBeVisible();
});

test('explains missing prices without displaying zero valuations', async ({ page }) => {
  await signInForMockRequests(page);
  await page.route('**/api/v1/accounts/1/analytics/summary', (route) => route.fulfill({ json: {
    ...portfolioFixture, asOf: null, valuationStatus: 'UNAVAILABLE', holdingsValue: null,
    totalEquity: null, unrealizedPnL: null,
    holdings: [{ ...portfolioFixture.holdings[0], price: null, marketValue: null, unrealizedPnL: null, weight: null }],
    issues: ['Missing completed price history for: AAPL'],
  } }));
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => route.fulfill({ json: fixture }));
  await page.goto('/analytics');
  await expect(page.getByRole('alert')).toContainText('Missing completed price history for: AAPL');
  await expect(page.getByTestId('total-equity')).toHaveText('Unavailable');
  await expect(page.getByTestId('cash-balance')).toHaveText('$5.00');
  await expect(page.locator('.allocation-fill')).toHaveCount(0);
  await expect(page.locator('polyline')).toBeVisible();
});

test('requires sign-in before opening analytics', async ({ page }) => {
  await page.goto('/analytics');
  await expect(page).toHaveURL(/\/signed-out$/);
});

test('shows loading and then a closing-price chart', async ({ page }) => {
  await signInForMockRequests(page);
  let release!: () => void;
  const pending = new Promise<void>((resolve) => { release = resolve; });
  await page.route('**/api/v1/market-data/prices/AAPL?**', async (route) => {
    await pending;
    await route.fulfill({ json: fixture });
  });
  await page.goto('/analytics');
  await expect(page.getByText('Loading AAPL price history…')).toBeVisible();
  release();
  await expect(page.getByRole('img', { name: 'AAPL daily closing prices in USD' })).toBeVisible();
  await expect(page.locator('.latest-price strong')).toHaveText('$253.00');
  await expect(page.getByRole('link', { name: 'Analytics', exact: true })).toHaveClass(/active/);
});

test('shows an empty state when the selected range has no stored prices', async ({ page }) => {
  await signInForMockRequests(page);
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => route.fulfill({ json: { ...fixture, prices: [] } }));
  await page.goto('/analytics');
  await expect(page.getByRole('heading', { name: 'No historical prices found' })).toBeVisible();
  await expect(page.locator('svg')).toHaveCount(0);
});

test('recovers from a server failure after Retry', async ({ page }) => {
  await signInForMockRequests(page);
  let calls = 0;
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => {
    calls += 1;
    return calls === 1
      ? route.fulfill({ status: 500, json: { message: 'History service unavailable' } })
      : route.fulfill({ json: fixture });
  });
  await page.goto('/analytics');
  await expect(page.getByRole('alert')).toContainText('History service unavailable');
  await page.getByRole('button', { name: 'Retry', exact: true }).click();
  await expect(page.locator('.latest-price strong')).toHaveText('$253.00');
  expect(calls).toBe(2);
});

test('sends edited dates to the API and blocks a reversed range', async ({ page }) => {
  await signInForMockRequests(page);
  const ranges: string[] = [];
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => {
    const query = new URL(route.request().url()).searchParams;
    ranges.push(`${query.get('from')}/${query.get('to')}`);
    return route.fulfill({ json: fixture });
  });
  await page.goto('/analytics');
  await expect(page.locator('polyline')).toBeVisible();
  await page.getByLabel('From', { exact: true }).fill('2025-10-01');
  await page.getByLabel('To', { exact: true }).fill('2025-10-02');
  await page.getByRole('button', { name: 'Load prices' }).click();
  await expect(page.locator('polyline')).toBeVisible();
  expect(ranges.at(-1)).toBe('2025-10-01/2025-10-02');
  await page.getByLabel('From', { exact: true }).fill('2025-10-03');
  await expect(page.getByRole('alert')).toHaveText('From must be on or before To.');
  await expect(page.getByRole('button', { name: 'Load prices' })).toBeDisabled();
});

test('selects database instruments and requests only their history with the chosen dates', async ({ page }) => {
  await signInForMockRequests(page);
  const requested: string[] = [];
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/market-data/prices/*?**', async route => {
    const url = new URL(route.request().url());
    const symbol = url.pathname.split('/').at(-1)!;
    requested.push(`${symbol}/${url.searchParams.get('from')}/${url.searchParams.get('to')}`);
    if (symbol === 'MSFT') await pending;
    await route.fulfill({ json: { ...fixture, symbol, prices: symbol === 'QQQ' ? [] : fixture.prices } });
  });
  await page.goto('/analytics');
  await expect(page.getByRole('img', { name: 'AAPL daily closing prices in USD' })).toBeVisible();
  await expect(page.locator('#price-symbol option')).toHaveCount(12);
  expect(requested).toHaveLength(1);
  await page.getByLabel('From', { exact: true }).fill('2025-10-01');
  await page.getByLabel('To', { exact: true }).fill('2025-10-02');
  await page.locator('#price-symbol').selectOption('MSFT');
  await expect(page.getByRole('heading', { name: 'MSFT · Historical prices' })).toBeVisible();
  await expect(page.getByText('Loading MSFT price history…')).toBeVisible();
  await expect(page.locator('app-price-chart')).toHaveCount(0);
  release();
  await expect(page.getByRole('img', { name: 'MSFT daily closing prices in USD' })).toBeVisible();
  await expect(page.getByText('Microsoft Corporation daily closing prices in USD.', { exact: false })).toBeVisible();
  expect(requested.at(-1)).toBe('MSFT/2025-10-01/2025-10-02');
  await page.locator('#price-symbol').selectOption('QQQ');
  await expect(page.getByText('No QQQ prices are stored', { exact: false })).toBeVisible();
  await expect(page.locator('app-price-chart')).toHaveCount(0);
  expect(requested).toHaveLength(3);
});

test('keeps the chart inside the page on mobile', async ({ page }) => {
  await signInForMockRequests(page);
  await page.route('**/api/v1/accounts/1/analytics/summary', (route) => route.fulfill({ json: portfolioFixture }));
  await page.route('**/api/v1/accounts/1/analytics/performance?**', (route) => route.fulfill({ json: performanceFixture }));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => route.fulfill({ json: fixture }));
  await page.goto('/analytics');
  await expect(page.locator('app-price-chart polyline')).toBeVisible();
  const widths = await page.locator('main').evaluate((element) => ({ width: element.getBoundingClientRect().width, viewport: window.innerWidth }));
  expect(widths.width).toBeLessThanOrEqual(widths.viewport);
});
