import { expect, test, type Page } from "@playwright/test";
import { E2E_PORTAL_PASSWORD } from "./helpers/backend";
import { readE2EState } from "./helpers/state";
import { fillPortalCaptcha, waitPastGrantElapsedFloor } from "./helpers/ui";
import { mkdirSync } from "node:fs";
import { join } from "node:path";

/**
 * R6 checklist row: real browser vs nginx-served production dist.
 * Handles leftover capacity-seed ads (home_popup) that stock journey-core does not dismiss.
 */

const SHOT_DIR = join(
  process.cwd(),
  "..",
  "deploy",
  "drills",
  "r6-browser-artifacts",
  "screenshots",
);

async function shot(page: Page, name: string): Promise<void> {
  mkdirSync(SHOT_DIR, { recursive: true });
  await page.screenshot({ path: join(SHOT_DIR, `${name}.png`), fullPage: true });
}

async function dismissHomePopupIfAny(page: Page): Promise<void> {
  const popup = page.getByTestId("ad-popup");
  if (await popup.isVisible().catch(() => false)) {
    // Vant Popup closeable icon
    const closeIcon = page.locator(".van-popup__close-icon, .van-icon-cross").first();
    if (await closeIcon.isVisible().catch(() => false)) {
      await closeIcon.click();
    } else {
      await page.keyboard.press("Escape");
    }
    await expect(popup).toBeHidden({ timeout: 5_000 });
  }
}

test.describe.configure({ mode: "serial" });

test.describe("r6-browser-static-spa", () => {
  let page: Page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test("static shell + asset 200 from real dist", async () => {
    const response = await page.goto("/");
    expect(response?.ok()).toBeTruthy();
    const html = await page.content();
    expect(html).toContain('id="app"');
    // Real Vite dist (not deploy/nginx/html-placeholders)
    expect(html).not.toContain("client placeholder");
    expect(html).not.toContain("portal.local placeholder");
    expect(html).toContain("/assets/");
    // Collect JS/CSS from document
    const assets = await page.evaluate(() =>
      [...document.querySelectorAll("script[src],link[rel=stylesheet][href]")].map((el) =>
        el.getAttribute("src") || el.getAttribute("href") || "",
      ),
    );
    expect(assets.some((a) => a.includes("/assets/") && a.endsWith(".js"))).toBe(true);
    expect(assets.some((a) => a.includes("/assets/") && a.endsWith(".css"))).toBe(true);
    for (const href of assets.filter((a) => a.startsWith("/assets/"))) {
      const res = await page.request.get(href);
      expect(res.status(), href).toBe(200);
    }
    await shot(page, "01-static-shell");
  });

  test("SPA try_files: hard refresh on deep client route still serves index", async () => {
    const deep = await page.goto("/mine/prizes", { waitUntil: "domcontentloaded" });
    expect(deep?.ok()).toBeTruthy();
    expect(deep?.headers()["content-type"] ?? "").toContain("text/html");
    const html = await page.content();
    expect(html).toContain('id="app"');
    expect(html).toContain("/assets/");
    // Hard reload
    const reloaded = await page.reload({ waitUntil: "domcontentloaded" });
    expect(reloaded?.ok()).toBeTruthy();
    await expect(page.locator("#app")).toBeAttached();
    await shot(page, "02-spa-refresh-mine-prizes");
  });

  test("register + login overlay path on real dist", async () => {
    const username = `r6b${Date.now().toString().slice(-8)}`;
    await page.goto("/register");
    await dismissHomePopupIfAny(page);
    await page.getByTestId("register-username").locator("input").fill(username);
    await page.getByTestId("register-username").locator("input").blur();
    await expect(page.getByTestId("register-username-hint")).toContainText(/可用/, { timeout: 15_000 });
    await page.getByTestId("register-password").locator("input").fill(E2E_PORTAL_PASSWORD);
    await fillPortalCaptcha(page);
    await page.getByTestId("register-agree").click();
    await expect(page.getByTestId("register-agree")).toHaveAttribute("aria-checked", "true");
    const pending = page.waitForResponse(
      (r) => r.url().includes("/api/common/auth/register") && r.request().method() === "POST",
    );
    await page.getByTestId("register-submit").click();
    const body = (await (await pending).json()) as { code?: unknown; message?: string };
    expect(body.code, body.message ?? JSON.stringify(body)).toBe(0);
    await expect(page).toHaveURL(/\/home$/, { timeout: 15_000 });
    await dismissHomePopupIfAny(page);
    await shot(page, "03-register-home");

    // Overlay login with seeded e2e_login1 (from globalSetup) on private page
    await page.evaluate(() => localStorage.clear());
    await page.goto("/mine");
    await expect(page.getByTestId("login-overlay")).toBeVisible({ timeout: 15_000 });
    const state = readE2EState();
    await page.getByTestId("login-username").locator("input").fill(state.loginUsername);
    await page.getByTestId("login-password").locator("input").fill(state.loginPassword);
    await fillPortalCaptcha(page);
    const pendingLogin = page.waitForResponse(
      (r) => r.url().includes("/api/common/auth/login") && r.request().method() === "POST",
    );
    await page.getByTestId("login-submit").click();
    expect(((await (await pendingLogin).json()) as { code?: unknown }).code).toBe(0);
    await expect(page.getByTestId("login-overlay")).toBeHidden({ timeout: 15_000 });
    await shot(page, "04-overlay-login-mine");
  });

  test("claim / complete / prize claim via UI", async () => {
    const state = readE2EState();
    // Fresh user so task is claimable
    const username = `r6c${Date.now().toString().slice(-8)}`;
    await page.evaluate(() => localStorage.clear());
    await page.goto("/register");
    await dismissHomePopupIfAny(page);
    await page.getByTestId("register-username").locator("input").fill(username);
    await page.getByTestId("register-username").locator("input").blur();
    await expect(page.getByTestId("register-username-hint")).toContainText(/可用/, { timeout: 15_000 });
    await page.getByTestId("register-password").locator("input").fill(E2E_PORTAL_PASSWORD);
    await fillPortalCaptcha(page);
    await page.getByTestId("register-agree").click();
    const pendingReg = page.waitForResponse(
      (r) => r.url().includes("/api/common/auth/register") && r.request().method() === "POST",
    );
    await page.getByTestId("register-submit").click();
    expect(((await (await pendingReg).json()) as { code?: unknown }).code).toBe(0);
    await expect(page).toHaveURL(/\/home$/, { timeout: 15_000 });
    await dismissHomePopupIfAny(page);

    await expect(page.getByTestId(`home-activity-${state.activityId}`)).toBeVisible({ timeout: 15_000 });
    await dismissHomePopupIfAny(page);
    await page.getByTestId(`home-activity-${state.activityId}`).click();
    await expect(page).toHaveURL(/\/activity/);
    await dismissHomePopupIfAny(page);
    await shot(page, "05-activity-list");

    const fixtureCard = page.getByTestId("task-card").filter({
      has: page.getByTestId(`task-card-action-${state.taskId}`),
    });
    await expect(fixtureCard).toBeVisible({ timeout: 15_000 });
    await fixtureCard.getByTestId("task-card-open").click();
    await expect(page).toHaveURL(new RegExp(`/task/${state.taskId}$`));
    await shot(page, "06-task-detail");

    const pendingStart = page.waitForResponse(
      (r) =>
        new URL(r.url()).pathname === `/api/common/task/${state.taskId}/start` &&
        r.request().method() === "POST",
    );
    await page.getByTestId("task-claim").click();
    const started = (await (await pendingStart).json()) as {
      code?: unknown;
      message?: string;
      data?: { instanceId?: number; instanceStatus?: string };
    };
    expect(started.code, started.message ?? JSON.stringify(started)).toBe(0);
    expect(started.data?.instanceStatus).toBe("IN_PROGRESS");
    const instanceId = started.data?.instanceId;
    expect(instanceId).toBeGreaterThan(0);
    await expect(page.getByTestId("task-step-action")).toBeVisible({ timeout: 15_000 });
    await waitPastGrantElapsedFloor();
    const pendingClick = page.waitForResponse(
      (r) =>
        new URL(r.url()).pathname === `/api/common/task/instances/${instanceId}/steps/clk/click` &&
        r.request().method() === "POST",
    );
    await page.getByTestId("task-step-action").click();
    const clickBody = (await (await pendingClick).json()) as {
      code?: unknown;
      message?: string;
      data?: { instanceStatus?: string; rewardFeedback?: Array<{ prizeName?: string; count?: number }> };
    };
    expect(clickBody.code, clickBody.message ?? JSON.stringify(clickBody)).toBe(0);
    expect(clickBody.data?.instanceStatus).toBe("COMPLETED");
    await expect(page.getByText("任务完成")).toBeVisible({ timeout: 15_000 });
    await shot(page, "07-task-completed");
    await page.getByRole("button", { name: "确认" }).click();
    await expect(page.getByTestId("task-prize-facts")).toBeVisible();

    await page.goto("/mine/prizes");
    await dismissHomePopupIfAny(page);
    const fixturePrize = page.getByTestId("prize-card").filter({
      has: page.getByText("e2e_core_pts points", { exact: true }),
    });
    await expect(fixturePrize).toBeVisible({ timeout: 15_000 });
    const claimButton = fixturePrize.locator("[data-testid^='prize-action-']");
    const recordId = Number((await claimButton.getAttribute("data-testid"))?.replace("prize-action-", ""));
    expect(recordId).toBeGreaterThan(0);
    const pendingClaim = page.waitForResponse(
      (r) =>
        new URL(r.url()).pathname === `/api/common/prize/records/${recordId}/claim` &&
        r.request().method() === "POST",
    );
    await claimButton.click();
    const claimed = (await (await pendingClaim).json()) as {
      code?: unknown;
      data?: { status?: string; fulfillmentStatus?: string };
    };
    expect(claimed.code).toBe(0);
    expect(claimed.data?.status).toBe("GRANTED");
    expect(claimed.data?.fulfillmentStatus).toBe("ARRIVED");
    await expect(fixturePrize.locator("[data-testid^='prize-action-']")).toHaveText("已到账", {
      timeout: 15_000,
    });
    await shot(page, "08-prize-claimed");

    await page.goto("/mine/points");
    await expect(page.getByTestId("points-balance")).toContainText("10", { timeout: 15_000 });
    await shot(page, "09-points-balance");
  });

  test("optional admin Host static smoke (curl-equivalent in browser context)", async ({
    request,
  }) => {
    const root = await request.get("http://127.0.0.1:18080/", {
      headers: { Host: "admin.local" },
      maxRedirects: 0,
    });
    expect([301, 302]).toContain(root.status());
    const consolePage = await request.get("http://127.0.0.1:18080/console/", {
      headers: { Host: "admin.local" },
    });
    expect(consolePage.status()).toBe(200);
    const body = await consolePage.text();
    expect(body).toContain("/console/assets/");
    expect(body).not.toContain("admin placeholder");
    expect(body).not.toContain("F16 TLS static stub");
    const deep = await request.get("http://127.0.0.1:18080/console/system/users", {
      headers: { Host: "admin.local" },
    });
    expect(deep.status()).toBe(200);
    expect(await deep.text()).toContain('id="app"');
  });
});
