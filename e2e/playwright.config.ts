import { defineConfig, devices } from '@playwright/test'

// Prefer localhost so Origin matches GATEWAY_ALLOWED_ORIGIN / WS CORS defaults.
const baseURL = process.env.E2E_BASE_URL || 'http://localhost:8888'

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
