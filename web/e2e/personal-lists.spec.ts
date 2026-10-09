import { expect, test, type Page, type Route } from "@playwright/test";
import type { components } from "../packages/shared/src/openapi/portal";

type MineTask = components["schemas"]["MineTaskView"];
type PointsTransaction = components["schemas"]["PointsPortalTxView"];
type Prize = components["schemas"]["PrizeCardView"];

async function respond(route: Route, data: unknown): Promise<void> {
  await route.fulfill({ json: { code: 0, message: "成功", data } });
}

async function preparePortal(
  page: Page,
  handle: (route: Route, url: URL) => Promise<boolean>,
): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem("mkt.portal.token", "client:browser-fixture");
  });
  await page.route((url) => url.pathname.startsWith("/api/"), async (route) => {
    const url = new URL(route.request().url());
    if (await handle(route, url)) {
      return;
    }
    if (url.pathname === "/api/common/auth/profile") {
      await respond(route, { userId: 91, username: "browser_fixture", nickname: "浏览器验收", pointsBalance: 42 });
    } else if (url.pathname === "/api/common/dict/task_category") {
      await respond(route, [{ label: "日常任务", value: "DAILY" }]);
    } else if (url.pathname === "/api/common/activity/activities") {
      await respond(route, []);
    } else if (url.pathname.startsWith("/api/common/ad/positions/")) {
      await respond(route, { code: url.pathname.split("/").at(-1), materials: [] });
    } else if (url.pathname === "/api/common/track/batch") {
      await respond(route, { accepted: 1, dropped: 0 });
    } else {
      await route.fulfill({ status: 404, json: { code: "common.not-found", message: "未准备的测试接口" } });
    }
  });
}

function pageOf<T>(rows: T[], url: URL) {
  const number = Number(url.searchParams.get("page") ?? "1");
  const size = Number(url.searchParams.get("pageSize") ?? "20");
  return { total: rows.length, records: rows.slice((number - 1) * size, number * size) };
}

test("task scrolling retries the same page and resets pagination on category selection", async ({ page }) => {
  const rows: MineTask[] = Array.from({ length: 25 }, (_, index) => ({
    instanceId: index + 1,
    taskId: index + 1,
    taskName: `日常任务 ${index + 1}`,
    category: "DAILY",
    status: "IN_PROGRESS",
    currentStepName: "继续完成当前步骤",
    startedAt: "2026-10-07T00:00:00Z",
  }));
  const queries: URL[] = [];
  let failSecondPage = true;
  await preparePortal(page, async (route, url) => {
    if (url.pathname !== "/api/common/task/mine") {
      return false;
    }
    queries.push(url);
    if (url.searchParams.get("page") === "2" && failSecondPage) {
      failSecondPage = false;
      await route.fulfill({ json: { code: "common.server-error", message: "续页失败，点击重试" } });
    } else {
      await respond(route, pageOf(url.searchParams.has("category") ? rows.slice(0, 2) : rows, url));
    }
    return true;
  });

  await page.goto("/mine/tasks");
  await expect(page.getByTestId("mine-task-card")).toHaveCount(20);
  await page.getByTestId("mine-task-card").last().scrollIntoViewIfNeeded();
  await expect(page.locator(".van-list__error-text")).toHaveText("续页失败，点击重试");
  await expect(page.getByTestId("mine-task-card")).toHaveCount(20);
  await page.locator(".van-list__error-text").click();
  await expect(page.getByTestId("mine-task-card")).toHaveCount(25);
  expect(queries.map((url) => url.searchParams.get("page"))).toEqual(["1", "2", "2"]);
  await page.getByTestId("mine-task-categories").scrollIntoViewIfNeeded();
  await page.getByTestId("mine-task-categories").click();
  await page.getByRole("menuitem", { name: "日常任务" }).click();
  await expect(page.getByTestId("mine-task-card")).toHaveCount(2);
  expect(queries.at(-1)?.searchParams.get("category")).toBe("DAILY");
  expect(queries.at(-1)?.searchParams.get("page")).toBe("1");
});

test("points balance and real ledger pagination survive failure before filtering by type", async ({ page }) => {
  const rows: PointsTransaction[] = Array.from({ length: 23 }, (_, index) => ({
    type: "EARN",
    amount: index + 1,
    balanceAfter: index + 1,
    sourceTaskId: index + 1,
    remark: `积分流水 ${index + 1}`,
    createdAt: "2026-10-07T00:00:00Z",
  }));
  const queries: URL[] = [];
  let failSecondPage = true;
  await preparePortal(page, async (route, url) => {
    if (url.pathname === "/api/common/points/balance") {
      await respond(route, { balance: 42 });
      return true;
    }
    if (url.pathname !== "/api/common/points/transactions") {
      return false;
    }
    queries.push(url);
    if (url.searchParams.get("page") === "2" && failSecondPage) {
      failSecondPage = false;
      await route.fulfill({ json: { code: "common.server-error", message: "积分续页失败，点击重试" } });
    } else {
      await respond(route, pageOf(url.searchParams.get("type") === "EARN" ? rows.slice(0, 2) : rows, url));
    }
    return true;
  });

  await page.goto("/mine/points");
  await expect(page.getByTestId("points-balance-amount")).toHaveText("42");
  await expect(page.getByTestId("points-row")).toHaveCount(20);
  await page.getByTestId("points-row").last().scrollIntoViewIfNeeded();
  await expect(page.locator(".van-list__error-text")).toHaveText("积分续页失败，点击重试");
  await page.locator(".van-list__error-text").click();
  await expect(page.getByTestId("points-row")).toHaveCount(23);
  expect(queries.map((url) => url.searchParams.get("page"))).toEqual(["1", "2", "2"]);
  await page.getByTestId("points-types").scrollIntoViewIfNeeded();
  await page.getByTestId("points-types").click();
  await page.getByRole("menuitem", { name: "获得", exact: true }).click();
  await expect(page.getByTestId("points-row")).toHaveCount(2);
  expect(queries.at(-1)?.searchParams.get("type")).toBe("EARN");
  expect(queries.at(-1)?.searchParams.get("page")).toBe("1");
});

test("claiming a pending prize reloads page one and leaves no gap on the next page", async ({ page }) => {
  let rows: Prize[] = Array.from({ length: 22 }, (_, index) => ({
    recordId: index + 1,
    prizeName: `待领奖品 ${index + 1}`,
    status: "WON",
    fulfillmentStatus: "NONE",
    expireAt: "2099-10-07T00:00:00Z",
    obtainedAt: "2026-10-07T00:00:00Z",
  }));
  const pendingPages: string[] = [];
  let claimCount = 0;
  await preparePortal(page, async (route, url) => {
    if (url.pathname === "/api/common/prize/list") {
      if (url.searchParams.get("tab") === "PENDING") {
        pendingPages.push(url.searchParams.get("page") ?? "1");
      }
      await respond(route, pageOf(rows, url));
      return true;
    }
    if (url.pathname === "/api/common/prize/records/1/claim") {
      claimCount += 1;
      rows = rows.filter((row) => row.recordId !== 1);
      await respond(route, { status: "GRANTED", fulfillmentStatus: "ARRIVED" });
      return true;
    }
    return false;
  });

  await page.goto("/mine/prizes");
  await expect(page.getByTestId("prize-card")).toHaveCount(20);
  await page.getByRole("tab", { name: "待领取" }).click();
  await expect.poll(() => pendingPages).toEqual(["1"]);
  await page.getByTestId("prize-action-1").click();
  await expect.poll(() => pendingPages).toEqual(["1", "1"]);
  await expect(page.getByTestId("prize-action-1")).toHaveCount(0);
  await expect(page.getByTestId("prize-action-21")).toBeAttached();
  await page.getByTestId("prize-card").last().scrollIntoViewIfNeeded();
  await expect(page.getByTestId("prize-card")).toHaveCount(21);
  await expect(page.getByTestId("prize-action-22")).toBeAttached();
  expect(pendingPages).toEqual(["1", "1", "2"]);
  expect(claimCount).toBe(1);
});

test("a login in another tab replaces the original account list from page one", async ({ page, context }) => {
  const accountA: MineTask[] = Array.from({ length: 24 }, (_, index) => ({
    instanceId: index + 1,
    taskId: index + 1,
    taskName: `账号 A 任务 ${index + 1}`,
    category: "DAILY",
    status: "IN_PROGRESS",
    currentStepName: "账号 A 当前步骤",
    startedAt: "2026-10-07T00:00:00Z",
  }));
  const accountB: MineTask[] = Array.from({ length: 2 }, (_, index) => ({
    instanceId: index + 101,
    taskId: index + 101,
    taskName: `账号 B 任务 ${index + 1}`,
    category: "DAILY",
    status: "IN_PROGRESS",
    currentStepName: "账号 B 当前步骤",
    startedAt: "2026-10-07T00:00:00Z",
  }));
  const requests: { authorization: string | undefined; page: string | null }[] = [];
  await preparePortal(page, async (route, url) => {
    if (url.pathname !== "/api/common/task/mine") {
      return false;
    }
    const authorization = route.request().headers().authorization;
    requests.push({ authorization, page: url.searchParams.get("page") });
    await respond(route, pageOf(authorization === "Bearer client:browser-b" ? accountB : accountA, url));
    return true;
  });

  await page.goto("/mine/tasks");
  await expect(page.getByTestId("mine-task-card")).toHaveCount(20);
  await page.getByTestId("mine-task-card").last().scrollIntoViewIfNeeded();
  await expect(page.getByTestId("mine-task-card")).toHaveCount(24);

  const otherTab = await context.newPage();
  await otherTab.route("**/storage-peer.html", (route) => route.fulfill({
    contentType: "text/html",
    body: "<!doctype html><title>同源会话测试标签页</title>",
  }));
  await otherTab.goto("/storage-peer.html");
  await otherTab.evaluate(() => {
    localStorage.setItem("mkt.portal.token", "client:browser-b");
  });

  await expect(page.getByTestId("mine-task-card")).toHaveCount(2);
  await expect(page.getByTestId("mine-task-card").first()).toContainText("账号 B 任务 1");
  await expect(page.getByTestId("mine-task-card").last()).toContainText("账号 B 任务 2");
  await expect(page.getByText(/账号 A/)).toHaveCount(0);
  expect(requests).toEqual([
    { authorization: "Bearer client:browser-fixture", page: "1" },
    { authorization: "Bearer client:browser-fixture", page: "2" },
    { authorization: "Bearer client:browser-b", page: "1" },
  ]);
  await otherTab.close();
});
