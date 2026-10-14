import { test, expect, Page } from '@playwright/test';

const fixture = {
  symbol: 'AAPL', currency: 'USD', from: '2025-10-01', to: '2025-10-02',
  prices: [
    { date: '2025-10-01', close: 250, adjustedClose: 248, volume: 100, source: 'test-fixture' },
    { date: '2025-10-02', close: 253, adjustedClose: 251, volume: 200, source: 'test-fixture' },
  ],
};

async function signInForMockRequests(page: Page) {
  // Browser-only fixture. These tests intercept history requests and do not
  // claim to authenticate against a real backend.
  await page.addInitScript(() => {
    const payload = btoa(JSON.stringify({ username: 'Analytics test', accountId: 1 }));
    localStorage.setItem('leap_access_token', `fixture.${payload}.fixture`);
  });
}

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

test('keeps the chart inside the page on mobile', async ({ page }) => {
  await signInForMockRequests(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/v1/market-data/prices/AAPL?**', (route) => route.fulfill({ json: fixture }));
  await page.goto('/analytics');
  await expect(page.locator('polyline')).toBeVisible();
  const widths = await page.locator('main').evaluate((element) => ({ width: element.getBoundingClientRect().width, viewport: window.innerWidth }));
  expect(widths.width).toBeLessThanOrEqual(widths.viewport);
});
