import { describe, expect, it } from "vitest";
import { CSRF_COOKIE, CSRF_HEADER, attachCsrf, compactParams, readCookie, shouldSkipUnauthorized } from "./http";

describe("admin http", () => {
  it("reads the non-HttpOnly CSRF cookie", () => {
    expect(readCookie(CSRF_COOKIE, "satoken=admin:x; csrfToken=csrf-1")).toBe("csrf-1");
  });

  it("attaches X-CSRF-Token on writes only", () => {
    const headers: Record<string, string> = {};
    attachCsrf({ method: "post", headers }, "csrfToken=csrf-1");
    expect(headers[CSRF_HEADER]).toBe("csrf-1");
    const getHeaders: Record<string, string> = {};
    attachCsrf({ method: "get", headers: getHeaders }, "csrfToken=csrf-1");
    expect(getHeaders[CSRF_HEADER]).toBeUndefined();
  });

  it("does not treat login 401 as session invalid", () => {
    expect(shouldSkipUnauthorized("/admin/auth/login")).toBe(true);
    expect(shouldSkipUnauthorized("/admin/identity/users")).toBe(false);
  });

  it("drops blank query params", () => {
    expect(compactParams({ username: "a", nickname: "", page: 1, roleId: undefined })).toEqual({
      username: "a",
      page: 1,
    });
  });
});
