import { defineConfig, devices } from "@playwright/test";

/**
 * R6 real-browser evidence against nginx-served production dist (not Vite).
 * Requires real client/admin dist mounted via deploy/docker-compose.r6-browser-dist.yml.
 */
const portalBase = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:18080";
const apiBase = process.env.E2E_API_BASE ?? "http://127.0.0.1:18080";

export default defineConfig({
  testDir: "./e2e",
  testMatch: /r6-browser-static-spa\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  reporter: "list",
  timeout: 180_000,
  globalSetup: "./e2e/global-setup.ts",
  outputDir: "../deploy/drills/r6-browser-artifacts/test-results",
  use: {
    trace: "off",
    ignoreHTTPSErrors: true,
    screenshot: "only-on-failure",
    video: "off",
    baseURL: portalBase,
    ...devices["Desktop Chrome"],
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  },
  projects: [
    {
      name: "portal-nginx-dist",
      use: { baseURL: portalBase },
    },
  ],
  metadata: { apiBase, mode: "nginx-production-dist" },
});
