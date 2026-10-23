import { expect, test } from '@playwright/test';
import { login, mockTradingApi } from './support/mock-api';

test.describe('Trading journeys', () => {
  test.beforeEach(async ({ page }) => {
    await mockTradingApi(page);
    await login(page);
  });

  test('loads dashboard data and navigates to order and blotter', async ({ page }) => {
    await expect(page.getByText('$100,000.00').first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Positions' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'AAPL' })).toBeVisible();

    await page.getByRole('button', { name: 'Place Order' }).click();
    await expect(page).toHaveURL(/\/order$/);
    await page.getByRole('link', { name: 'Blotter' }).click();
    await expect(page).toHaveURL(/\/blotter$/);
  });

  test('places a BUY order and shows confirmation', async ({ page }) => {
    await page.goto('/order');
    await page.getByLabel('Instrument / symbol').fill('AAPL');
    await page.getByLabel('Quantity').fill('1');
    await page.getByLabel('Price').fill('100');
    await page.getByRole('button', { name: 'Place Order' }).click();

    await expect(page.getByRole('heading', { name: 'Order response received' })).toBeVisible();
    await expect(page.getByRole('status')).toContainText('FILLED');
    await expect(page.locator('.confirmation-summary')).toContainText('AAPL');
  });

  test('places a SELL order and shows confirmation', async ({ page }) => {
    await page.goto('/order');
    await page.getByText('SELL', { exact: true }).click();
    await page.getByLabel('Instrument / symbol').fill('AAPL');
    await page.getByLabel('Quantity').fill('1');
    await page.getByLabel('Price').fill('100');
    await page.getByRole('button', { name: 'Place Order' }).click();

    await expect(page.getByRole('heading', { name: 'Order response received' })).toBeVisible();
    await expect(page.locator('.confirmation-notice')).toContainText('FILLED');
    await expect(page.locator('.confirmation-summary')).toContainText('SELL');
  });

  test('shows order history newest first', async ({ page }) => {
    await page.goto('/blotter');
    await expect(page.getByRole('heading', { name: 'Order History', exact: true })).toBeVisible();

    const rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('14 Oct 2026');
    await expect(rows.nth(1)).toContainText('13 Oct 2026');
    await expect(rows.nth(0)).toContainText('AAPL');
    await expect(rows.nth(1)).toContainText('MSFT');
  });

  test('logs out and clears the session', async ({ page }) => {
    await page.getByRole('button', { name: 'Logout' }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.evaluate(() => localStorage.getItem('leap_access_token'))).resolves.toBeNull();
    await expect(page.getByRole('button', { name: 'Login' })).toBeVisible();
  });
});