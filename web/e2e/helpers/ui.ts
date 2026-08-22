import type { Page } from "@playwright/test";
import { isAuthRateLimited, withAuthRateLimitRetry } from "./rateLimit";
import { waitRedisGet } from "./redis";

function isCaptchaGet(
  item: { url(): string; request(): { method(): string } },
  path: string,
): boolean {
  return item.url().includes(path) && item.request().method() === "GET";
}

/**
 * Pages fetch captcha in onMounted. waitForResponse before that GET finishes
 * matches the in-flight id; refresh then replaces captchaId and submit uses
 * a stale code. Wait for the image (first GET done) before refreshing.
 */
async function fillCaptchaFromRefresh(
  page: Page,
  path: string,
  realm: "admin" | "portal",
): Promise<string> {
  await page.getByTestId("login-captcha-refresh").waitFor({ state: "visible" });
  // onMounted GET may 429; image stays hidden. Refresh below retries the same bucket.
  await page
    .getByTestId("login-captcha-image")
    .waitFor({ state: "visible", timeout: 5_000 })
    .catch(() => undefined);
  const payload = await withAuthRateLimitRetry(
    async () => {
      const pending = page.waitForResponse((item) => isCaptchaGet(item, path));
      await page.getByTestId("login-captcha-refresh").click();
      return (await (await pending).json()) as { code?: unknown; data?: { captchaId?: string } };
    },
    (body) => body.code,
  );
  if (isAuthRateLimited(payload)) {
    throw new Error(`${path} failed: ${JSON.stringify(payload)}`);
  }
  return captchaFromResponsePayload(payload, realm);
}

async function captchaFromResponsePayload(
  payload: { data?: { captchaId?: string } },
  realm: "admin" | "portal",
): Promise<string> {
  const captchaId = payload.data?.captchaId;
  if (!captchaId) {
    throw new Error("captchaId missing from UI captcha response");
  }
  return waitRedisGet(`captcha:${realm}:${captchaId}`);
}

export async function fillPortalCaptcha(page: Page): Promise<void> {
  const code = await fillCaptchaFromRefresh(page, "/api/common/captcha", "portal");
  await page.getByTestId("login-captcha").locator("input").fill(code);
}

export async function fillAdminCaptcha(page: Page): Promise<void> {
  const code = await fillCaptchaFromRefresh(page, "/admin/captcha", "admin");
  await page.getByTestId("login-captcha").locator("input").fill(code);
}

/**
 * V4 R-e / V1 `risk.rule.task-complete-min-seconds`: GRANT rejects when
 * elapsedSeconds is non-null and < 5. Same-request REWARD (elapsed=null) skips;
 * CLICK then REWARD measures from instance start. Wait 6s so Duration.toSeconds is ≥ 5.
 */
export const GRANT_ELAPSED_FLOOR_MS = 6_000;

export async function waitPastGrantElapsedFloor(startedAtMs = Date.now()): Promise<void> {
  const remaining = GRANT_ELAPSED_FLOOR_MS - (Date.now() - startedAtMs);
  if (remaining > 0) {
    await new Promise<void>((resolve) => {
      setTimeout(resolve, remaining);
    });
  }
}
