import { expect, test, type Page } from "@playwright/test";
import { E2E_PORTAL_PASSWORD } from "./helpers/backend";
import { readE2EState } from "./helpers/state";
import { fillPortalCaptcha, waitPastGrantElapsedFloor } from "./helpers/ui";

const PORTAL_TOKEN_KEY = "mkt.portal.token";

/**
 * P0 portal journey against staging compose (design §7.9).
 * Base URL: PLAYWRIGHT_BASE_URL. API via Vite proxy → E2E_API_BASE (compose nginx).
 */

test.describe.configure({ mode: "serial" });

test.describe("journey-core", () => {
  test("anonymous home stays on the activity hub", async ({ page }) => {
    await page.goto("/home");
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page).toHaveURL(/\/home/);
    await expect(page.getByTestId("home-signin-card")).toBeVisible();
    await expect(page.getByTestId("home-activity-list").or(page.getByTestId("home-empty"))).toBeVisible();
  });

  test("R32.1 anonymous visit of a business page redirects to login and keeps the return path", async ({
    page,
  }) => {
    const state = readE2EState();
    await page.goto("/mine");
    await expect(page).toHaveURL(/\/login/);
    expect(new URL(page.url()).searchParams.get("redirect")).toBe("/mine");
    await page.getByTestId("login-username").locator("input").fill(state.loginUsername);
    await page.getByTestId("login-password").locator("input").fill(state.loginPassword);
    await fillPortalCaptcha(page);
    await page.getByTestId("login-submit").click();
    await expect(page).toHaveURL(/\/mine$/);
  });
});

test.describe("journey-core registered path", () => {
  let page: Page;
  let oldToken = "";

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test("R32.4 register with agreement checked auto-logs in to home", async () => {
    const username = `e2e${Date.now()}`;
    await page.goto("/register");
    await page.getByTestId("register-username").locator("input").fill(username);
    await page.getByTestId("register-username").locator("input").blur();
    await expect(page.getByTestId("register-username-hint")).toContainText(/可用/);
    await page.getByTestId("register-password").locator("input").fill(E2E_PORTAL_PASSWORD);
    await fillPortalCaptcha(page);
    await page.getByTestId("register-agree").click();
    await expect(page.getByTestId("register-agree")).toHaveAttribute("aria-checked", "true");
    await expect(page.getByTestId("register-submit")).toBeEnabled();
    const pending = page.waitForResponse(
      (response) =>
        response.url().includes("/api/common/auth/register") && response.request().method() === "POST",
    );
    await page.getByTestId("register-submit").click();
    const body = (await (await pending).json()) as { code?: unknown; message?: string };
    expect(body.code, body.message ?? JSON.stringify(body)).toBe(0);
    await expect(page).toHaveURL(/\/home$/, { timeout: 15_000 });
  });

  test("R34.5 home list emits task.card.exposure via POST /api/common/track/batch", async () => {
    const state = readE2EState();
    if (!page.url().includes("/home")) {
      await page.goto("/home");
    }
    await expect(page.getByTestId("home-signin-card")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId(`home-activity-${state.activityId}`)).toBeVisible();
    await expect(page.getByTestId("task-card")).toHaveCount(0);
    const pending = page.waitForRequest(
      (request) =>
        request.url().includes("/api/common/track/batch") &&
        request.method() === "POST" &&
        JSON.stringify(request.postDataJSON() ?? {}).includes("task.card.exposure"),
      { timeout: 15_000 },
    );
    await page.getByTestId(`home-activity-${state.activityId}`).click();
    await expect(page).toHaveURL(/\/activity/);
    await expect(page.getByTestId("task-card").first()).toBeVisible({ timeout: 15_000 });
    const request = await pending;
    const body = request.postDataJSON() as { events?: Array<{ code?: string }> };
    expect(body.events?.some((event) => event.code === "task.card.exposure")).toBe(true);
  });

  test("claim a listed task then click-complete the current step", async () => {
    const state = readE2EState();
    await page.goto("/home");
    await expect(page.getByTestId("home-signin-card")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("task-card")).toHaveCount(0);
    await page.getByTestId(`home-activity-${state.activityId}`).click();
    await expect(page).toHaveURL(/\/activity/);
    await expect(page.getByTestId("task-card").first()).toBeVisible({ timeout: 15_000 });
    await page.getByTestId("task-card-open").first().click();
    await expect(page.getByTestId("task-complete-sheet")).toBeVisible();
    await page.getByTestId("task-claim").click();
    await expect(page).toHaveURL(/\/activity/);
    await expect(page).not.toHaveURL(/\/task\/\d+/);
    await expect(page.getByTestId("task-complete-sheet")).toBeVisible();
    await expect(page.getByTestId("task-step-action")).toBeVisible({ timeout: 15_000 });
    await waitPastGrantElapsedFloor();
    const pendingClick = page.waitForResponse(
      (response) =>
        response.url().includes("/steps/") &&
        response.url().includes("/click") &&
        response.request().method() === "POST",
    );
    await page.getByTestId("task-step-action").click();
    const clickBody = (await (await pendingClick).json()) as { code?: unknown; message?: string };
    expect(clickBody.code, clickBody.message ?? JSON.stringify(clickBody)).toBe(0);
    await expect(page.getByText("任务完成")).toBeVisible({ timeout: 15_000 });
    await page.getByRole("button", { name: "确认" }).click();
  });

  test("R34.4 timeline updates immediately and shows result feedback", async () => {
    await expect(page.getByTestId("task-detail-ended")).toBeVisible();
    await expect(page.getByTestId("task-detail-ended")).toContainText(/已完成/);
  });

  test("claim a prize on 我的奖品", async () => {
    await page.goto("/mine/prizes");
    await expect(page.getByTestId("prize-card").first()).toBeVisible({ timeout: 15_000 });
    await page.locator("[data-testid^='prize-action-']").first().click();
    await expect(page.getByText("已到账")).toBeVisible({ timeout: 15_000 });
  });

  test("R35.4 points page refreshes balance immediately after grant", async () => {
    const pending = page.waitForResponse(
      (response) =>
        response.url().includes("/api/common/points/balance") && response.request().method() === "GET",
    );
    await page.goto("/mine/points");
    await pending;
    await expect(page.getByTestId("points-balance")).toContainText("10");
  });

  test("R33.5 logout invalidates the previous session", async () => {
    oldToken = await page.evaluate((key) => localStorage.getItem(key) ?? "", PORTAL_TOKEN_KEY);
    expect(oldToken).not.toBe("");
    await page.goto("/mine");
    await page.getByTestId("entry-logout").click();
    await page.getByRole("button", { name: "确认" }).click();
    await expect(page).toHaveURL(/\/login/);
    const replay = await page.request.get("/api/common/task/mine", {
      headers: { Authorization: `Bearer ${oldToken}`, "X-Device-Id": "e2e-device", "X-Client-Platform": "WEB" },
    });
    expect(replay.status()).toBe(401);
    await page.goto("/mine");
    await expect(page).toHaveURL(/\/login/);
  });
});
