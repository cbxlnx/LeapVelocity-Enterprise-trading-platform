import { expect, test } from '@playwright/test';
import { mockTradingApi } from './support/mock-api';

async function openLogin(page: Parameters<typeof mockTradingApi>[0]): Promise<void> {
  await mockTradingApi(page);
  await page.goto('/login');
}

test.describe('Login Component', () => {
  test.beforeEach(async ({ page }) => {
    await openLogin(page);
  });

  test('loads the login page and form controls', async ({ page }) => {
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.getByLabel('Username')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Login' })).toBeDisabled();
  });

  test('shows required and minimum-length validation messages', async ({ page }) => {
    const username = page.getByLabel('Username');
    const password = page.getByLabel('Password');

    await username.focus();
    await username.blur();
    await expect(page.getByText('Username is required')).toBeVisible();
    await username.fill('ab');
    await expect(page.getByText('Username must be at least 3 characters')).toBeVisible();

    await password.focus();
    await password.blur();
    await expect(page.getByText('Password is required')).toBeVisible();
    await password.fill('12345');
    await expect(page.getByText('Password must be at least 6 characters')).toBeVisible();
  });

  test('enables submit only when both fields are valid', async ({ page }) => {
    const submit = page.getByRole('button', { name: 'Login' });
    await page.getByLabel('Username').fill('validuser');
    await page.getByLabel('Password').fill('validpass123');
    await expect(submit).toBeEnabled();

    await page.getByLabel('Username').fill('ab');
    await expect(submit).toBeDisabled();
    await page.getByLabel('Username').fill('validuser');
    await page.getByLabel('Password').fill('12345');
    await expect(submit).toBeDisabled();
  });

  test('supports keyboard submission and redirects after login', async ({ page }) => {
    await page.getByLabel('Username').fill('john');
    await page.getByLabel('Password').fill('password');
    await page.getByLabel('Password').press('Enter');
    await page.waitForURL('**/dashboard');
    await expect(page.getByRole('heading', { name: 'Welcome, john' })).toBeVisible();
  });

  test('stores an access token after successful login', async ({ page }) => {
    await page.getByLabel('Username').fill('john');
    await page.getByLabel('Password').fill('password');
    await page.getByRole('button', { name: 'Login' }).click();
    await page.waitForURL('**/dashboard');
    await expect(page.evaluate(() => localStorage.getItem('leap_access_token'))).resolves.toBeTruthy();
  });

  test('shows a login error for rejected credentials', async ({ page }) => {
    await page.unroute('**/auth/login');
    await page.route('**/auth/login', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Invalid credentials' }),
      });
    });

    await page.getByLabel('Username').fill('invaliduser');
    await page.getByLabel('Password').fill('wrongpass123');
    await page.getByRole('button', { name: 'Login' }).click();
    await expect(page.getByRole('alert')).toContainText('Invalid credentials');
  });

  test('exposes the expected accessibility attributes', async ({ page }) => {
    await expect(page.getByLabel('Username')).toHaveAttribute('aria-required', 'true');
    await expect(page.getByLabel('Password')).toHaveAttribute('aria-required', 'true');
    await expect(page.locator('form')).toBeVisible();
    await expect(page.locator('h1')).toBeVisible();
  });

  test('is keyboard navigable', async ({ page }) => {
    await page.getByLabel('Username').fill('validuser');
    await page.getByLabel('Password').fill('validpass123');
    await page.getByLabel('Username').focus();
    await expect(page.getByLabel('Username')).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Password')).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Login' })).toBeFocused();
  });

  test('is usable on mobile and tablet viewports', async ({ page }) => {
    for (const viewport of [{ width: 375, height: 667 }, { width: 768, height: 1024 }]) {
      await page.setViewportSize(viewport);
      await expect(page.locator('.login-card')).toBeVisible();
      await expect(page.getByLabel('Username')).toBeVisible();
    }
  });
});
