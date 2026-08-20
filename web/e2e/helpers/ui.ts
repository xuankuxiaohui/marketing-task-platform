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

export async function fillPortalCaptcha(page: Page): Promise<void> {
  const pending = page.waitForResponse(
    (item) => item.url().includes("/api/common/captcha") && item.request().method() === "GET",
  );
  await page.getByTestId("login-captcha-refresh").click();
  const code = await captchaFromResponse(await pending, "portal");
  await page.getByTestId("login-captcha").locator("input").fill(code);
}

export async function fillAdminCaptcha(page: Page): Promise<void> {
  const pending = page.waitForResponse(
    (item) => item.url().includes("/admin/captcha") && item.request().method() === "GET",
  );
  await page.getByTestId("login-captcha-refresh").click();
  const code = await captchaFromResponse(await pending, "admin");
  await page.getByTestId("login-captcha").fill(code);
}
