import { describe, expect, it } from "vitest";
import {
  AUTH_HEADER,
  CLIENT_PLATFORM,
  DEVICE_HEADER,
  PLATFORM_HEADER,
  attachPortalHeaders,
  shouldSkipUnauthorized,
} from "./http";

describe("portal http", () => {
  it("skips session handling on anonymous auth endpoints", () => {
    expect(shouldSkipUnauthorized("/api/common/auth/login")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/auth/register")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/captcha")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/auth/username-available")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/track/batch")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/ad/positions/home_banner")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/ad/materials/1/dismiss")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/points/balance")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/points/transactions")).toBe(false);
    expect(shouldSkipUnauthorized("/api/common/activity/activities")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/activity/3")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/task/list")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/task/8/detail")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/activity/3/participate")).toBe(false);
    expect(shouldSkipUnauthorized("/api/common/task/8/start")).toBe(false);
    expect(shouldSkipUnauthorized("/api/common/auth/profile")).toBe(false);
  });

  it("attaches bearer and device headers", () => {
    const headers: Record<string, string> = {};
    attachPortalHeaders({ headers }, "client:tok", "550e8400-e29b-41d4-a716-446655440000");
    expect(headers[AUTH_HEADER]).toBe("Bearer client:tok");
    expect(headers[DEVICE_HEADER]).toBe("550e8400-e29b-41d4-a716-446655440000");
    expect(headers[PLATFORM_HEADER]).toBe(CLIENT_PLATFORM);
  });
});
