import { test } from "@playwright/test";

/**
 * P0 portal journey (design §7.9). Implement against staging compose in task 43.
 * Mock payloads MUST come from springdoc JSON in packages/shared/openapi (NFR 可维护性 2).
 */
test.describe("journey-core", () => {
  test.skip("R32.1 anonymous visit of a business page redirects to login and keeps the return path", async () => {
    // /mine → /login?redirect=/mine → login → back to /mine
  });

  test.skip("R32.4 register with agreement checked auto-logs in to home", async () => {
    // fill username/password/captcha, check agreement, land on /home
  });

  test.skip("R34.5 home list emits task.card.exposure via POST /api/common/track/batch", async () => {
    // intercept /api/common/track/batch and assert event code task.card.exposure
  });

  test.skip("claim a listed task then click-complete the current step", async () => {
    // start instance, click step action
  });

  test.skip("R34.4 timeline updates immediately and shows result feedback", async () => {
    // after click, done/current/idle tones and reward dialog
  });

  test.skip("claim a prize on 我的奖品", async () => {
    // WON → claim → arrived copy
  });

  test.skip("R35.4 points page refreshes balance immediately after grant", async () => {
    // open /mine/points, assert GET /api/common/points/balance is not a stale session cache
  });

  test.skip("R33.5 logout invalidates the previous session", async () => {
    // logout, reuse old bearer against a business page → login redirect
  });
});
