import { fileURLToPath } from "node:url";
import { defineConfig, devices } from "@playwright/test";

const port = Number(process.env.PLAYWRIGHT_PERSONAL_LISTS_PORT ?? "4185");
if (!Number.isInteger(port) || port < 1024 || port > 65535) {
  throw new Error("PLAYWRIGHT_PERSONAL_LISTS_PORT must be an integer between 1024 and 65535");
}
const baseURL = `http://127.0.0.1:${port}`;

/** Browser regressions for the real portal UI, with API responses isolated per test. */
export default defineConfig({
  testDir: "./e2e",
  testMatch: "personal-lists.spec.ts",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  reporter: "list",
  timeout: 30_000,
  outputDir: "./test-results/personal-lists",
  use: {
    ...devices["Desktop Chrome"],
    baseURL,
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : undefined,
  },
  webServer: {
    command: `"${process.execPath}" node_modules/vite/bin/vite.js --host 127.0.0.1 --port ${port} --strictPort`,
    cwd: fileURLToPath(new URL("./apps/client/", import.meta.url)),
    url: baseURL,
    reuseExistingServer: false,
    timeout: 60_000,
  },
});
