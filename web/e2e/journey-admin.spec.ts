import { test } from "@playwright/test";

/**
 * Admin journey (design §7.9 / R14.9). Implement against staging compose in task 43.
 * Base URL: PLAYWRIGHT_ADMIN_BASE_URL. Types and mocks from packages/shared/openapi/admin.json.
 */
test.describe("journey-admin", () => {
  test.skip("login to the admin console", async () => {
    // captcha + credentials → dashboard
  });

  test.skip("save a task aggregate on the canvas", async () => {
    // definition edit → persist steps/branches/actions
  });

  test.skip("reject an invalid expression on the canvas", async () => {
    // expression sandbox error stays on the form
  });

  test.skip("publish a task version", async () => {
    // confirm dialog → published snapshot
  });

  test.skip("R14.9 query instances after publish", async () => {
    // instance list shows the published task
  });
});
