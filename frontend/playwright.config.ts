/// <reference types="node" />

import { defineConfig, devices } from '@playwright/test';
import { env } from 'node:process';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!env['CI'],
  retries: env['CI'] ? 2 : 0,
  workers: env['CI'] ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:4200',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'on-first-retry',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chromium'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: !env['CI'],
  },
});
