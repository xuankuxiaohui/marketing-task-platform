import { defineConfig, devices } from "@playwright/test";

/**
 * Journey tests against staging compose (design §7.9 / task 43).
 * Base URLs come from env — MUST NOT hard-code a developer IP (13-frontend-engineering §8).
 */
const portalBase = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:5174";
const adminBase = process.env.PLAYWRIGHT_ADMIN_BASE_URL ?? "http://127.0.0.1:5173";
const apiBase = process.env.E2E_API_BASE ?? "http://127.0.0.1:18080";

export default defineConfig({
  testDir: "./e2e",
  testMatch: /journey-.*\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  reporter: "list",
  timeout: 60_000,
  globalSetup: "./e2e/global-setup.ts",
  use: {
    trace: "off",
    ignoreHTTPSErrors: true,
  },
  webServer: [
    {
      command: "pnpm --filter client exec vite --host 127.0.0.1 --port 5174",
      url: portalBase,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: {
        PORTAL_PROXY_TARGET: apiBase,
      },
    },
    {
      command: "pnpm --filter admin exec vite --host 127.0.0.1 --port 5173",
      url: adminBase,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: {
        ADMIN_PROXY_TARGET: apiBase,
      },
    },
  ],
  projects: [
    {
      name: "portal",
      testMatch: /journey-core\.spec\.ts/,
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
        baseURL: portalBase,
      },
    },
    {
      name: "admin",
      testMatch: /journey-admin\.spec\.ts/,
      use: {
        ...devices["Desktop Chrome"],
        baseURL: adminBase,
      },
    },
  ],
});
