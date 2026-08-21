import { expect, test, type Page } from "@playwright/test";
import { startTask, unlockedAdminPassword } from "./helpers/backend";
import { loadDotEnv } from "./helpers/env";
import { readE2EState } from "./helpers/state";
import { fillAdminCaptcha } from "./helpers/ui";

/**
 * Admin journey against staging compose (design §7.9 / R14.9).
 * Base URL: PLAYWRIGHT_ADMIN_BASE_URL.
 */

test.describe.configure({ mode: "serial" });

test.describe("journey-admin", () => {
  let page: Page;
  let taskId = 0;
  const taskCode = `e2eadm${Date.now()}`;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test("login to the admin console", async () => {
    const env = loadDotEnv();
    const state = readE2EState();
    const password = state.adminPassword ?? env.MKT_INIT_ADMIN_PASSWORD;
    await page.goto("/login");
    await page.getByTestId("login-username").fill("admin");
    await page.getByTestId("login-password").fill(password);
    await fillAdminCaptcha(page);
    await page.getByTestId("login-submit").click();
    await expect(page).toHaveURL(/\/(dashboard|change-password)/);
    if (/\/change-password/.test(page.url())) {
      await page.getByTestId("change-password-old").fill(password);
      await page.getByTestId("change-password-new").fill(unlockedAdminPassword(password));
      await page.getByTestId("change-password-submit").click();
    }
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test("save a task aggregate on the canvas", async () => {
    const state = readE2EState();
    await page.goto("/task/definitions");
    await expect(page.getByTestId("task-definition-page")).toBeVisible({ timeout: 15_000 });
    await page.getByTestId("task-create").click();
    await expect(page.getByTestId("task-edit-page")).toBeVisible();
    await page.getByTestId("task-code").fill(taskCode);
    await page.getByTestId("task-name").fill("e2e admin journey");
    await page.getByTestId("step-code").fill("clk");
    await page.getByTestId("step-name").fill("click");
    await page.getByTestId("step-type").selectOption("CLICK");
    await page.getByTestId("step-add").click();
    await page.getByTestId("step-code").fill("rwd");
    await page.getByTestId("step-name").fill("reward");
    await page.getByTestId("step-type").selectOption("REWARD");
    await page.getByTestId("step-prize").fill(String(state.prizeId));
    await page.getByTestId("step-add").click();
    await page.getByTestId("edge-from").fill("clk");
    await page.getByTestId("edge-to").fill("rwd");
    await page.getByTestId("edge-add").click();
    await page.getByTestId("task-save").click();
    await expect(page).toHaveURL(/\/task\/definitions\/edit\/\d+/, { timeout: 15_000 });
    const match = page.url().match(/edit\/(\d+)/);
    taskId = Number(match?.[1]);
    expect(taskId).toBeGreaterThan(0);
    await expect(page.getByTestId("task-canvas-steps")).toContainText("clk");
    await expect(page.getByTestId("task-canvas-steps")).toContainText("rwd");
  });

  test("reject an invalid expression on the canvas", async () => {
    await page.getByTestId("expr-input").fill('"".getClass().forName("java.lang.Runtime")');
    await page.getByTestId("expr-validate").click();
    await expect(page.getByTestId("expr-result")).toBeVisible();
    await expect(page.getByTestId("expr-result")).not.toHaveText("表达式合法");
    await expect(page).toHaveURL(/\/task\/definitions\/edit\/\d+/);
  });

  test("publish a task version", async () => {
    const pending = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        /\/admin\/task\/definitions\/\d+\/publish/.test(response.url()),
    );
    await page.getByTestId("task-publish").click();
    const payload = (await (await pending).json()) as { data?: { requiresConfirm?: boolean } };
    if (payload.data?.requiresConfirm) {
      await expect(page.getByTestId("confirm-dialog")).toBeVisible();
      const confirmed = page.waitForResponse(
        (response) =>
          response.request().method() === "POST" &&
          /\/admin\/task\/definitions\/\d+\/publish/.test(response.url()),
      );
      await page.getByTestId("confirm-ok").click();
      await confirmed;
      await expect(page.getByTestId("confirm-dialog")).toHaveCount(0);
    }
    await expect(page.getByTestId("page-error")).toHaveCount(0);
  });

  test("R14.9 query instances after publish", async () => {
    const state = readE2EState();
    await startTask(state.portalToken, taskId);
    await page.goto("/task/instances");
    await expect(page.getByTestId("instance-page")).toBeVisible({ timeout: 15_000 });
    await page.getByTestId("filter-task-id").fill(String(taskId));
    await page.getByTestId("instance-query").click();
    await expect(page.getByTestId("instance-table")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("instance-table")).toContainText(String(taskId));
  });
});
