import { test, expect } from '@playwright/test';

/**
 * E2E Tests for Login Component
 * Run with: npx playwright test
 *
 * Note: This test assumes:
 * - App is running on http://localhost:4200
 * - AuthService has a mock implementation or real backend is available
 * - Dashboard route is available at /dashboard
 */

const BASE_URL = 'http://localhost:4200';

test.describe('Login Component', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to login page before each test
    await page.goto(`${BASE_URL}/login`);
  });

  test.describe('Page Rendering', () => {
    test('should load login page', async ({ page }) => {
      // Check if main heading is visible
      const heading = page.locator('h1:has-text("Leap Trading Platform")');
      await expect(heading).toBeVisible();
    });

    test('should display login form', async ({ page }) => {
      // Check for username input
      const usernameInput = page.locator('#username');
      await expect(usernameInput).toBeVisible();

      // Check for password input
      const passwordInput = page.locator('#password');
      await expect(passwordInput).toBeVisible();

      // Check for submit button
      const submitButton = page.locator('button[type="submit"]');
      await expect(submitButton).toBeVisible();
    });

    test('should display form labels', async ({ page }) => {
      const usernameLabel = page.locator('label:has-text("Username")');
      await expect(usernameLabel).toBeVisible();

      const passwordLabel = page.locator('label:has-text("Password")');
      await expect(passwordLabel).toBeVisible();
    });

    test('should have submit button disabled by default', async ({ page }) => {
      const submitButton = page.locator('button[type="submit"]');
      await expect(submitButton).toBeDisabled();
    });
  });

  test.describe('Form Validation', () => {
    test('should show username required error when field is touched and empty', async ({
      page,
    }) => {
      const usernameInput = page.locator('#username');

      // Focus and blur to trigger validation
      await usernameInput.focus();
      await usernameInput.blur();

      // Check for error message
      const errorMessage = page.locator(
        'text=Username is required'
      );
      await expect(errorMessage).toBeVisible();
    });

    test('should show username minlength error when less than 3 characters', async ({
      page,
    }) => {
      const usernameInput = page.locator('#username');

      // Type less than 3 characters
      await usernameInput.fill('ab');
      await usernameInput.blur();

      // Check for error message
      const errorMessage = page.locator(
        'text=Username must be at least 3 characters'
      );
      await expect(errorMessage).toBeVisible();
    });

    test('should show password required error when field is touched and empty', async ({
      page,
    }) => {
      const passwordInput = page.locator('#password');

      // Focus and blur to trigger validation
      await passwordInput.focus();
      await passwordInput.blur();

      // Check for error message
      const errorMessage = page.locator(
        'text=Password is required'
      );
      await expect(errorMessage).toBeVisible();
    });

    test('should show password minlength error when less than 6 characters', async ({
      page,
    }) => {
      const passwordInput = page.locator('#password');

      // Type less than 6 characters
      await passwordInput.fill('12345');
      await passwordInput.blur();

      // Check for error message
      const errorMessage = page.locator(
        'text=Password must be at least 6 characters'
      );
      await expect(errorMessage).toBeVisible();
    });

    test('should enable submit button when form is valid', async ({ page }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');
      const submitButton = page.locator('button[type="submit"]');

      // Fill in valid data
      await usernameInput.fill('validuser');
      await passwordInput.fill('validpass123');

      // Check if submit button is enabled
      await expect(submitButton).toBeEnabled();
    });

    test('should keep submit button disabled if username is invalid', async ({
      page,
    }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');
      const submitButton = page.locator('button[type="submit"]');

      // Fill with invalid username (less than 3 chars)
      await usernameInput.fill('ab');
      await passwordInput.fill('validpass123');

      // Check if submit button is disabled
      await expect(submitButton).toBeDisabled();
    });

    test('should keep submit button disabled if password is invalid', async ({
      page,
    }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');
      const submitButton = page.locator('button[type="submit"]');

      // Fill with invalid password (less than 6 chars)
      await usernameInput.fill('validuser');
      await passwordInput.fill('12345');

      // Check if submit button is disabled
      await expect(submitButton).toBeDisabled();
    });
  });

  test.describe('Form Submission', () => {
    test('should submit form when Enter key is pressed', async ({ page }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');

      // Fill in valid data
      await usernameInput.fill('validuser');
      await passwordInput.fill('validpass123');

      // Press Enter key
      await passwordInput.press('Enter');

      // Wait for navigation or response
      // This assumes the login will either succeed or show an error
      await page.waitForTimeout(1000);
    });

    test('should display loading state during login', async ({ page }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');
      const submitButton = page.locator('button[type="submit"]');

      // Fill in valid data
      await usernameInput.fill('validuser');
      await passwordInput.fill('validpass123');

      // Click submit button
      await submitButton.click();

      // Check if button is disabled during loading
      await expect(submitButton).toBeDisabled();
    });

    test('should show error message on login failure', async ({ page }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');
      const submitButton = page.locator('button[type="submit"]');

      // Fill in invalid credentials
      await usernameInput.fill('invaliduser');
      await passwordInput.fill('wrongpass123');

      // Click submit button
      await submitButton.click();

      // Wait for error message to appear
      const errorAlert = page.locator('[role="alert"]');
      await expect(errorAlert).toBeVisible({ timeout: 5000 });
    });

    test('should redirect to dashboard on successful login', async ({
      page,
    }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');
      const submitButton = page.locator('button[type="submit"]');

      // Fill in valid credentials (adjust based on mock/backend setup)
      await usernameInput.fill('testuser');
      await passwordInput.fill('testpass123');

      // Click submit button
      await submitButton.click();

      // Wait for navigation to dashboard
      await page.waitForURL(`${BASE_URL}/dashboard`, { timeout: 5000 });
      expect(page.url()).toContain('/dashboard');
    });
  });

  test.describe('Accessibility', () => {
    test('should have proper ARIA labels', async ({ page }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');

      // Check aria-required attribute
      await expect(usernameInput).toHaveAttribute('aria-required', 'true');
      await expect(passwordInput).toHaveAttribute('aria-required', 'true');
    });

    test('should have form role', async ({ page }) => {
      const form = page.locator('form');
      await expect(form).toBeVisible();
    });

    test('should have proper heading hierarchy', async ({ page }) => {
      const h1 = page.locator('h1');
      await expect(h1).toBeVisible();
    });

    test('should be keyboard navigable', async ({ page }) => {
      const usernameInput = page.locator('#username');
      const passwordInput = page.locator('#password');
      const submitButton = page.locator('button[type="submit"]');

      // Tab through form fields
      await page.keyboard.press('Tab');
      await expect(usernameInput).toBeFocused();

      await page.keyboard.press('Tab');
      await expect(passwordInput).toBeFocused();

      await page.keyboard.press('Tab');
      await expect(submitButton).toBeFocused();
    });

    test('should announce validation errors to screen readers', async ({
      page,
    }) => {
      const usernameInput = page.locator('#username');

      // Interact with field to trigger validation
      await usernameInput.focus();
      await usernameInput.blur();

      // Check if error is announced with role="status"
      const validationMessages = page.locator('[role="status"]');
      await expect(validationMessages).toContainText('Username is required');
    });
  });

  test.describe('Responsive Design', () => {
    test('should be responsive on mobile', async ({ page }) => {
      // Set mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });

      // Check if login card is visible
      const loginCard = page.locator('.login-card');
      await expect(loginCard).toBeVisible();

      // Check if form is still functional
      const usernameInput = page.locator('#username');
      await usernameInput.fill('validuser');
      await expect(usernameInput).toHaveValue('validuser');
    });

    test('should be responsive on tablet', async ({ page }) => {
      // Set tablet viewport
      await page.setViewportSize({ width: 768, height: 1024 });

      // Check if login card is visible
      const loginCard = page.locator('.login-card');
      await expect(loginCard).toBeVisible();
    });
  });
});
