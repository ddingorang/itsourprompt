import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',

  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,

  reporter: [
  ['list'],
  ['html', { outputFolder: 'playwright-report', open: 'never' }],
    [
      'junit',
      {
        outputFile: './test-results/junit-report.xml',
        embedAnnotationsAsProperties: true,
      },
    ],
  ],

  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:5173',

    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
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
});