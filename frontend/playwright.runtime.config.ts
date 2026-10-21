import { defineConfig, devices } from '@playwright/test';

/** Opt-in checks against the running Compose deployment; no response interception. */
export default defineConfig({
  testDir: './e2e', testMatch: 'risk-runtime.spec.ts', workers: 1, retries: 0, reporter: 'line',
  use: { baseURL: process.env['RISK_RUNTIME_URL'] ?? 'http://localhost:8085',
    screenshot: 'only-on-failure', trace: 'off', video: 'off' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chromium'] } }],
});
