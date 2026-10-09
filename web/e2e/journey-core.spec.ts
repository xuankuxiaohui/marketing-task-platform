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

  test("R32.1 current private-page behavior opens overlay login and preserves the page", async ({
    page,
  }) => {
    const state = readE2EState();
    await page.goto("/mine");
    await expect(page).toHaveURL(/\/mine$/);
    const overlay = page.getByTestId("login-overlay");
    await expect(overlay).toBeVisible();
    const anonymous = await page.request.get("/api/common/task/mine", {
      headers: { "X-Device-Id": "e2e-device", "X-Client-Platform": "WEB" },
    });
    expect(anonymous.status()).toBe(401);
    await page.getByTestId("login-username").locator("input").fill(state.loginUsername);
    await page.getByTestId("login-password").locator("input").fill(state.loginPassword);
    await fillPortalCaptcha(page);
    const pendingLogin = page.waitForResponse(
      (response) =>
        response.url().includes("/api/common/auth/login") && response.request().method() === "POST",
    );
    await page.getByTestId("login-submit").click();
    expect(((await (await pendingLogin).json()) as { code?: unknown }).code).toBe(0);
    await expect(overlay).toBeHidden();
    await expect(page).toHaveURL(/\/mine$/);
    await expect(page.getByTestId("profile-nickname")).not.toHaveText("");
  });
});

test.describe("journey-core registered path", () => {
  let page: Page;
  let oldToken = "";
  let completedRewardFeedback: Array<{ prizeName?: string; count?: number }> | undefined;

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
      (request) => {
        if (!request.url().includes("/api/common/track/batch") || request.method() !== "POST") {
          return false;
        }
        const body = request.postDataJSON() as { events?: Array<{ code?: string; props?: { taskId?: number } }> };
        return body.events?.some((event) => event.code === "task.card.exposure" && event.props?.taskId === state.taskId) === true;
      },
      { timeout: 15_000 },
    );
    await page.getByTestId(`home-activity-${state.activityId}`).click();
    await expect(page).toHaveURL(/\/activity/);
    await expect(page.getByTestId(`task-card-action-${state.taskId}`)).toBeVisible({ timeout: 15_000 });
    const request = await pending;
    const body = request.postDataJSON() as { events?: Array<{ code?: string; props?: { taskId?: number } }> };
    expect(body.events?.some((event) => event.code === "task.card.exposure" && event.props?.taskId === state.taskId)).toBe(true);
  });

  test("claim a listed task then click-complete the current step", async () => {
    const state = readE2EState();
    await page.goto("/home");
    await expect(page.getByTestId("home-signin-card")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("task-card")).toHaveCount(0);
    await page.getByTestId(`home-activity-${state.activityId}`).click();
    await expect(page).toHaveURL(/\/activity/);
    const fixtureCard = page.getByTestId("task-card").filter({
      has: page.getByTestId(`task-card-action-${state.taskId}`),
    });
    await expect(fixtureCard).toHaveCount(1, { timeout: 15_000 });
    await expect(fixtureCard).toBeVisible({ timeout: 15_000 });
    await fixtureCard.getByTestId("task-card-open").click();
    const detailPath = new RegExp(`/task/${state.taskId}$`);
    await expect(page).toHaveURL(detailPath);
    await expect(page.getByTestId("task-steps-preview")).toContainText("click");
    await expect(page.getByTestId("task-steps-preview")).toContainText("reward");
    const pendingStart = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/common/task/${state.taskId}/start` &&
        response.request().method() === "POST",
    );
    await page.getByTestId("task-claim").click();
    const started = (await (await pendingStart).json()) as {
      code?: unknown;
      data?: { instanceId?: number; instanceStatus?: string };
    };
    expect(started.code).toBe(0);
    expect(started.data?.instanceStatus).toBe("IN_PROGRESS");
    expect(started.data?.instanceId).toBeGreaterThan(0);
    await expect(page).toHaveURL(detailPath);
    await expect(page.getByTestId("task-step-action")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("task-current-step")).toContainText("click");
    await waitPastGrantElapsedFloor();
    const pendingClick = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/common/task/instances/${started.data?.instanceId}/steps/clk/click` &&
        response.request().method() === "POST",
    );
    await page.getByTestId("task-step-action").click();
    const clickBody = (await (await pendingClick).json()) as {
      code?: unknown;
      message?: string;
      data?: { instanceStatus?: string; rewardFeedback?: Array<{ prizeName?: string; count?: number }> };
    };
    expect(clickBody.code, clickBody.message ?? JSON.stringify(clickBody)).toBe(0);
    expect(clickBody.data?.instanceStatus).toBe("COMPLETED");
    completedRewardFeedback = clickBody.data?.rewardFeedback;
    expect(completedRewardFeedback).toHaveLength(1);
    expect(completedRewardFeedback?.[0]?.count).toBe(1);
    await expect(page.getByText("任务完成")).toBeVisible({ timeout: 15_000 });
    await page.getByRole("button", { name: "确认" }).click();
  });

  test("R34.4 completed task immediately shows its prize facts and stops offering steps", async () => {
    await expect(page.getByTestId("task-prize-facts")).toBeVisible();
    await expect(page.getByTestId("task-prize-name")).toHaveText("e2e_core_pts points");
    await expect(page.getByTestId("task-prize-type")).toContainText("POINTS");
    await expect(page.getByTestId("task-claim")).toHaveCount(0);
    await expect(page.getByTestId("task-step-action")).toHaveCount(0);
    await expect(page.getByTestId("task-current-step")).toHaveCount(0);
  });

  test("claim a prize on 我的奖品", async () => {
    await page.goto("/mine/prizes");
    const fixturePrize = page.getByTestId("prize-card").filter({
      has: page.getByText("e2e_core_pts points", { exact: true }),
    });
    await expect(fixturePrize).toHaveCount(1, { timeout: 15_000 });
    await expect(fixturePrize).toBeVisible({ timeout: 15_000 });
    const claimButton = fixturePrize.locator("[data-testid^='prize-action-']");
    const recordId = Number((await claimButton.getAttribute("data-testid"))?.replace("prize-action-", ""));
    expect(recordId).toBeGreaterThan(0);
    const pendingClaim = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/common/prize/records/${recordId}/claim` &&
        response.request().method() === "POST",
    );
    await claimButton.click();
    const claimed = (await (await pendingClaim).json()) as {
      code?: unknown;
      data?: { status?: string; fulfillmentStatus?: string };
    };
    expect(claimed.code).toBe(0);
    expect(claimed.data?.status).toBe("GRANTED");
    expect(claimed.data?.fulfillmentStatus).toBe("ARRIVED");
    await expect(fixturePrize.locator("[data-testid^='prize-action-']")).toHaveText("已到账", { timeout: 15_000 });
  });

  test("R35.4 points page refreshes balance immediately after grant", async () => {
    const pending = page.waitForResponse(
      (response) =>
        response.url().includes("/api/common/points/balance") && response.request().method() === "GET",
    );
    await page.goto("/mine/points");
    const balance = (await (await pending).json()) as { code?: unknown; data?: { balance?: number } };
    expect(balance.code).toBe(0);
    expect(balance.data?.balance).toBe(10);
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
    expect(await page.evaluate((key) => localStorage.getItem(key), PORTAL_TOKEN_KEY)).toBeNull();
    await page.goto("/mine");
    await expect(page).toHaveURL(/\/mine$/);
    await expect(page.getByTestId("login-overlay")).toBeVisible();
  });

  test("R34.4 completion feedback names the actual awarded prize", () => {
    expect(completedRewardFeedback).toEqual([{ prizeName: "e2e_core_pts points", count: 1 }]);
  });
});
