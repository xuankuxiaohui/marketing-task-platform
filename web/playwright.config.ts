import { defineConfig, devices } from "@playwright/test";

/**
 * Journey skeletons for design §7.9. Full staging compose runs are task 43.
 * Base URLs come from env — MUST NOT hard-code a developer IP (13-frontend-engineering §8).
 */
const portalBase = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:4173";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  reporter: "list",
  use: {
    baseURL: portalBase,
    trace: "off",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
