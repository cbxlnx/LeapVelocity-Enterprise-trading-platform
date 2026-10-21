import { expect, test, type Page, type Response } from '@playwright/test';
import type { HistoricalReplay, PortfolioRisk } from '../src/app/features/analytics/models/portfolio-risk.model';

const token = process.env['RISK_RUNTIME_TOKEN'];
const amount = (value: number | null, prefix = '', suffix = '') => value == null ? 'Unavailable'
  : prefix + value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + suffix;

// Read through the HTTP client so browser response-body eviction cannot affect assertions.
// Both calls reach the real service; no routes or data are mocked.
async function snapshot(page: Page, response: Response): Promise<PortfolioRisk> {
  expect(response.status()).toBe(200);
  const result = await page.request.get(response.url(), { headers: { Authorization: `Bearer ${token}` } });
  expect(result.status()).toBe(200);
  return result.json();
}

test.describe('Deployed account risk dashboard', () => {
  test.skip(!token, 'Supply a short-lived RISK_RUNTIME_TOKEN for the intended account.');
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(value => localStorage.setItem('leap_access_token', value), token!);
  });

  test('working panels use the real response and unsupported views are absent', async ({ page }) => {
    const pending = page.waitForResponse(r => r.url().includes('/analytics/risk?'));
    await page.goto('/risk');
    const response = await pending;
    expect(response.status()).toBe(200);
    const risk = await snapshot(page, response);
    const summary = page.locator('.kpi-grid');
    await expect(summary).toBeVisible();
    await expect(summary).toContainText(amount(risk.valuation.totalEquity, '$'));
    for (const value of risk.valueAtRisk) await expect(summary).toContainText(amount(value.lossAmount, '$'));
    await expect(summary).toContainText(amount(risk.metrics.annualisedVolatility.value, '', '%'));
    await expect(summary).toContainText(amount(risk.metrics.beta.value));
    for (const holding of risk.valuation.holdings) await expect(page.locator('.concentration-card tbody')).toContainText(holding.symbol);
    await expect(page.locator('.contribution-card')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'By sector', exact: true })).toHaveCount(0);
    await expect(page.locator('.concentration-card thead th')).toHaveCount(3);
    await expect(page.locator('.waterfall-section')).toHaveCount(0);
    for (const removed of ['Not yet assessed', 'No result yet', 'no result yet', 'Volatility contribution', 'Sector exposure', 'Sector increments']) await expect(page.locator('main.risk-dashboard')).not.toContainText(removed);
    await expect(page.getByRole('heading', { name: 'Monte Carlo distribution', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '30 days', exact: true })).toHaveCount(0);
    if (risk.monteCarlo?.status === 'READY') {
      await expect(page.locator('.histogram-chart canvas')).toBeVisible();
      await expect(page.locator('.es-callout')).toContainText(amount(risk.monteCarlo.expectedShortfallAmount, '$'));
      expect(risk.monteCarlo.bins.reduce((total, bin) => total + bin.count, 0)).toBe(risk.monteCarlo.simulations);
    } else await expect(page.locator('.histogram-chart')).toHaveCount(0);
    if (risk.series.status === 'READY') {
      await expect(page.locator('.drawdown-chart canvas')).toBeVisible();
      expect(risk.series.wealth.length).toBe(risk.series.returns.length + 1);
    } else {
      await expect(page.locator('.drawdown-chart')).toHaveCount(0);
    }
    if (!risk.valuation.holdings.length) await expect(page.locator('.concentration-card')).toContainText('No invested holdings');
    await page.screenshot({ path: process.env['RISK_RUNTIME_SCREENSHOT'] ?? '/tmp/leap-real-risk-runtime.png', fullPage: true });
  });

  test('history, holding period and refresh reach the actual backend', async ({ page }) => {
    await page.goto('/risk');
    await expect(page.locator('.kpi-grid')).toBeVisible();
    const shorter = page.waitForResponse(r => r.url().includes('/analytics/risk?period=3M'));
    await page.getByRole('combobox', { name: 'Historical window', exact: true }).selectOption('3M');
    const shortRisk = await snapshot(page, await shorter);
    expect(shortRisk.parameters.period).toBe('3M');
    await expect(page.locator('.kpi-grid')).toBeVisible();
    const horizon = page.waitForResponse(r => r.url().includes('holdingPeriodDays=10'));
    await page.getByRole('button', { name: '10 days', exact: true }).click();
    expect((await snapshot(page, await horizon)).parameters.holdingPeriodDays).toBe(10);
    await expect(page.locator('.kpi-grid')).toBeVisible();
    const refresh = page.waitForResponse(r => r.url().includes('/analytics/risk?'));
    await page.getByRole('button', { name: 'Refresh', exact: false }).click();
    const refreshed = await snapshot(page, await refresh);
    expect(refreshed.parameters.period).toBe('3M');
    expect(refreshed.parameters.holdingPeriodDays).toBe(10);
    await expect(page.locator('.kpi-grid')).toBeVisible();
  });

  test('user price shocks reconcile against real holdings without changing cash', async ({ page }) => {
    const pending = page.waitForResponse(r => r.url().includes('/analytics/risk?'));
    await page.goto('/risk');
    const risk = await snapshot(page, await pending);
    test.skip(!risk.valuation.holdings.length || risk.valuation.valuationStatus !== 'READY', 'No invested holdings in this account.');
    await expect(page.locator('.kpi-grid')).toBeVisible();
    await page.getByLabel('Market shock percentage', { exact: true }).fill('-10');
    const shockResponse = page.waitForResponse(response => response.url().endsWith('/analytics/risk/shock') && response.request().method() === 'POST');
    await page.locator('.shock-card').getByRole('button', { name: 'Run', exact: true }).click();
    const response = await shockResponse;
    expect(response.status()).toBe(200);
    const shock = await response.json();
    expect(shock.status).toBe('READY');
    await expect(page.locator('.impact-panel')).toContainText(amount(shock.totalPnL, '$'));
    for (const holding of shock.holdings) await expect(page.locator('.breakdown-table')).toContainText(amount(holding.pnl, '$'));
    await expect(page.locator('.impact-panel')).toContainText(amount(risk.valuation.cashBalance, '$'));
    await expect(page.locator('.waterfall-chart canvas')).toBeVisible();
    await expect(page.getByRole('button', { name: 'By sector', exact: true })).toHaveCount(0);
  });

  test('runtime authentication, account isolation, 99% limits and Analytics remain intact', async ({ page, request }) => {
    const pending = page.waitForResponse(r => r.url().includes('/analytics/risk?'));
    await page.goto('/risk');
    const risk = await snapshot(page, await pending);
    const path = `/api/v1/accounts/${risk.valuation.accountId}/analytics`;
    const headers = { Authorization: `Bearer ${token}` };
    expect((await request.get(`${path}/risk`)).status()).toBe(401);
    expect((await request.get(`/api/v1/accounts/${risk.valuation.accountId + 1}/analytics/risk`, { headers })).status()).toBe(403);
    expect((await request.get(`${path}/risk?holdingPeriodDays=30`, { headers })).status()).toBe(422);
    const strict = await request.get(`${path}/risk?period=2Y&confidenceLevel=0.99`, { headers });
    expect(strict.status()).toBe(200);
    const data: PortfolioRisk = await strict.json();
    for (const value of data.valueAtRisk) {
      expect(value.requiredObservations).toBe(500);
      if (value.observations < 500) { expect(value.status).not.toBe('READY'); expect(value.lossAmount).toBeNull(); }
    }
    for (const suffix of ['/risk/shock', '/risk/replay/MARCH_2020']) {
      expect((await request.post(`${path}${suffix}`, { data: { priceChangePercent: -10 } })).status()).toBe(401);
      expect((await request.post(`/api/v1/accounts/${risk.valuation.accountId + 1}/analytics${suffix}`, { headers, data: { priceChangePercent: -10 } })).status()).toBe(403);
    }
    expect((await request.get(`${path}/summary`, { headers })).status()).toBe(200);
    expect((await request.get(`${path}/performance?period=1Y`, { headers })).status()).toBe(200);
  });

  test('only historical scenarios with complete real coverage are displayed', async ({ page }) => {
    const ids = ['CRISIS_2008', 'MARCH_2020'];
    const responses = ids.map(id => page.waitForResponse(response => response.url().endsWith(`/analytics/risk/replay/${id}`)));
    await page.goto('/risk');
    await expect(page.locator('.kpi-grid')).toBeVisible();
    let ready = 0;
    for (let index = 0; index < ids.length; index++) {
      const id = ids[index];
      const response = await responses[index];
      expect(response.status()).toBe(200);
      const result: HistoricalReplay = await response.json();
      expect(result.scenario.id).toBe(id);
      const run = page.getByRole('button', { name: `Run replay ${id}`, exact: true });
      if (result.status === 'READY') {
        ready++;
        expect(result.validatedHoldings).toBe(result.expectedHoldings);
        expect(result.holdings.length).toBe(result.valuation.holdings.length);
        await expect(run).toBeVisible();
        await expect(page.locator('.stress-card')).toContainText(amount(result.totalPnL, '$'));
        await page.getByRole('button', { name: `View breakdown ${id}`, exact: true }).click();
        await expect(page.locator('.breakdown-table')).toContainText(amount(result.totalPnL, '$'));
        await expect(page.locator('.waterfall-chart canvas')).toBeVisible();
        const fresh = page.waitForResponse(response => response.url().endsWith(`/analytics/risk/replay/${id}`));
        await run.click();
        const refreshed: HistoricalReplay = await (await fresh).json();
        expect(refreshed.status).toBe('READY');
        await expect(page.locator('.breakdown-table')).toContainText(amount(refreshed.totalPnL, '$'));
      } else {
        expect(result.totalPnL).toBeNull();
        expect(result.reasons.length).toBeGreaterThan(0);
        await expect(run).toHaveCount(0);
      }
    }
    await expect(page.locator('.historical-scenario')).toHaveCount(ready);
    await expect(page.locator('.replay-loading')).toHaveCount(0);
    if (!ready) await expect(page.locator('.stress-card')).toHaveCount(0);
  });
});
