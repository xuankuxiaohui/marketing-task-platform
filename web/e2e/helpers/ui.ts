import type { Page, Response } from "@playwright/test";
import { waitRedisGet } from "./redis";

async function captchaFromResponse(response: Response, realm: "admin" | "portal"): Promise<string> {
  const payload = (await response.json()) as { data?: { captchaId?: string } };
  const captchaId = payload.data?.captchaId;
  if (!captchaId) {
    throw new Error("captchaId missing from UI captcha response");
  }
  return waitRedisGet(`captcha:${realm}:${captchaId}`);
}

function isCaptchaGet(item: { url(): string; request(): { method(): string } }, path: string): boolean {
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
  await page.getByTestId("login-captcha-image").waitFor({ state: "visible" });
  const pending = page.waitForResponse((item) => isCaptchaGet(item, path));
  await page.getByTestId("login-captcha-refresh").click();
  return captchaFromResponse(await pending, realm);
}

export async function fillPortalCaptcha(page: Page): Promise<void> {
  const code = await fillCaptchaFromRefresh(page, "/api/common/captcha", "portal");
  await page.getByTestId("login-captcha").locator("input").fill(code);
}

export async function fillAdminCaptcha(page: Page): Promise<void> {
  const code = await fillCaptchaFromRefresh(page, "/admin/captcha", "admin");
  await page.getByTestId("login-captcha").fill(code);
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
