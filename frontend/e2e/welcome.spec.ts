import { expect, test } from '@playwright/test';
import { ACCESS_TOKEN, mockTradingApi } from './support/mock-api';

for (const theme of ['light', 'dark'] as const) {
  for (const width of [1440, 1024, 768, 390, 320]) {
    test(`welcome renders in ${theme} at ${width}px without overflow`, async ({
      page,
    }, testInfo) => {
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(error.message));
      page.on('console', (message) => {
        if (message.type() === 'error') errors.push(message.text());
      });
      await page.setViewportSize({ width, height: width < 760 ? 844 : 1000 });
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.addInitScript(
        (savedTheme) => localStorage.setItem('leap-ui-theme', savedTheme),
        theme,
      );
      await page.goto('/');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        'Your Skill andour Capital Join Forces',
      );
      await expect(page.getByRole('link', { name: 'Get funded' })).toBeVisible();
      await expect(page.locator('.hero-stats > div')).toHaveCount(4);
      await expect(page.locator('html')).toHaveClass(theme === 'dark' ? /dark-theme/ : /^$/);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      // Check the copy never collides with decorative animals at phone sizes.
      if (width < 760) {
        const copy = await page.locator('.hero-copy').boundingBox();
        const art = await page.locator('app-geometric-market-illustration').boundingBox();
        expect(art!.y).toBeGreaterThanOrEqual(copy!.y + copy!.height);
        await expect(page.getByRole('button', { name: 'Open navigation' })).toBeVisible();
      } else {
        await expect(page.getByRole('link', { name: 'How it works' })).toBeVisible();
      }
      await page.screenshot({
        path: testInfo.outputPath(`welcome-${theme}-${width}.png`),
        fullPage: true,
        animations: 'disabled',
      });
      expect(errors).toEqual([]);
    });
  }
}

test('theme selection persists across reloads and existing auth pages', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await expect(page.locator('html')).toHaveClass(/dark-theme/);
  expect(await page.evaluate(() => localStorage.getItem('leap-ui-theme'))).toBe('dark');
  await page.reload();
  await expect(page.getByRole('button', { name: 'Switch to light mode' })).toBeVisible();
  await page.getByRole('link', { name: 'Client area', exact: true }).first().click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('html')).toHaveClass(/dark-theme/);
  await expect(page.locator('.topbar')).toHaveCount(0);
  await page.getByRole('button', { name: 'Switch to light mode' }).click();
  await page.goto('/');
  await expect(page.locator('html')).not.toHaveClass(/dark-theme/);
  await page.getByRole('link', { name: 'Get funded' }).click();
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.getByRole('heading', { name: 'Create your workspace' })).toBeVisible();
});

test('mobile navigation supports keyboard dismissal, real anchors and client area', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  const toggle = page.getByRole('button', { name: 'Open navigation' });
  await expect(page.getByRole('navigation')).toBeHidden();
  await toggle.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('button', { name: 'Close navigation' })).toHaveAttribute(
    'aria-expanded',
    'true',
  );
  await page.keyboard.press('Escape');
  await expect(toggle).toBeFocused();
  await expect(page.getByRole('navigation')).toBeHidden();
  await toggle.click();
  await page.getByRole('link', { name: 'How it works' }).click();
  await expect(page).toHaveURL(/#how-it-works$/);
  await expect(page.getByRole('navigation')).toBeHidden();
  await expect(page.locator('#how-it-works')).toBeInViewport();
  await page.evaluate(() => scrollTo(0, 0));
  await toggle.click();
  await page.getByRole('navigation').getByRole('link', { name: 'Client area' }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('FAQ links scroll to accessible, keyboard-operated disclosures', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'FAQ', exact: true }).click();
  await expect(page.locator('#faq')).toBeInViewport();
  const question = page.locator('summary').nth(1);
  await question.focus();
  await page.keyboard.press('Enter');
  await expect(
    page.getByText('No. Registration creates a platform account.', { exact: false }),
  ).toBeVisible();
  await page.getByRole('link', { name: 'About us' }).click();
  await expect(page.locator('#about-us')).toBeInViewport();
});

test('Trading opens the existing guarded dashboard and preserves the workspace header', async ({
  page,
}) => {
  await mockTradingApi(page);
  await page.addInitScript(
    (token) => localStorage.setItem('leap_access_token', token),
    ACCESS_TOKEN,
  );
  await page.goto('/');
  await page.getByRole('link', { name: 'Trading', exact: true }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Welcome, john' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Analytics' })).toBeVisible();
  await expect(page.getByRole('banner').getByRole('button', { name: 'Logout' })).toBeVisible();
  await expect(page.locator('.welcome-topbar')).toHaveCount(0);
});
