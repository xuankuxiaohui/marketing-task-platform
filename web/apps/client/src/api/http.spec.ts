import type { AxiosAdapter, InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { writePortalToken } from "@/utils/token";
import {
  AUTH_HEADER,
  CLIENT_PLATFORM,
  DEVICE_HEADER,
  PLATFORM_HEADER,
  attachPortalHeaders,
  createHttp,
  setUnauthorizedHandler,
  shouldSkipUnauthorized,
} from "./http";

describe("portal http", () => {
  beforeEach(() => {
    writePortalToken("");
  });

  afterEach(() => {
    setUnauthorizedHandler(undefined);
    writePortalToken("");
  });

  it("skips session handling on anonymous auth endpoints", () => {
    expect(shouldSkipUnauthorized("/api/common/auth/login")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/auth/register")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/captcha")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/auth/username-available")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/track/batch")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/ad/positions/home_banner")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/ad/materials/1/dismiss")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/points/balance")).toBe(false);
    expect(shouldSkipUnauthorized("/api/common/points/transactions")).toBe(false);
    expect(shouldSkipUnauthorized("/api/common/signin/activities")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/signin/1/calendar")).toBe(true);
    expect(shouldSkipUnauthorized("/api/common/signin/1/checkin")).toBe(false);
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

  function unauthorizedRequest(url = "/api/common/task/mine") {
    const instance = createHttp();
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    let complete: (() => void) | undefined;
    let started: (() => void) | undefined;
    const ready = new Promise<void>((resolve) => {
      started = resolve;
    });
    const response = new Promise<void>((resolve) => {
      complete = resolve;
    });
    let sent: InternalAxiosRequestConfig | undefined;
    const adapter: AxiosAdapter = async (config) => {
      sent = config;
      started?.();
      await response;
      return {
        config,
        data: { code: "auth.session.expired", message: "会话已过期" },
        headers: {},
        status: 401,
        statusText: "Unauthorized",
      };
    };
    const pending = instance.get(url, { adapter });
    return { handler, pending, ready, finish: () => complete?.(), sent: () => sent };
  }

  it("handles a current session 401 with its reason", async () => {
    writePortalToken("client:a");
    const request = unauthorizedRequest();
    await request.ready;
    expect(request.sent()?.headers.get(AUTH_HEADER)).toBe("Bearer client:a");
    request.finish();
    await request.pending;
    expect(request.handler).toHaveBeenCalledOnce();
    expect(request.handler).toHaveBeenCalledWith({ code: "auth.session.expired", message: "会话已过期" });
  });

  it("ignores an old account 401 after login switches the token", async () => {
    writePortalToken("client:a");
    const request = unauthorizedRequest();
    await request.ready;
    writePortalToken("client:b");
    request.finish();
    await request.pending;
    expect(request.handler).not.toHaveBeenCalled();
  });

  it("ignores a former session 401 after logout", async () => {
    writePortalToken("client:a");
    const request = unauthorizedRequest();
    await request.ready;
    writePortalToken("");
    request.finish();
    await request.pending;
    expect(request.handler).not.toHaveBeenCalled();
  });

  it("ignores a tokenless request 401 when login has completed meanwhile", async () => {
    const request = unauthorizedRequest();
    await request.ready;
    expect(request.sent()?.headers.get(AUTH_HEADER)).toBeUndefined();
    writePortalToken("client:b");
    request.finish();
    await request.pending;
    expect(request.handler).not.toHaveBeenCalled();
  });

  it("still guides a guest who accesses a protected endpoint", async () => {
    const request = unauthorizedRequest();
    await request.ready;
    request.finish();
    await request.pending;
    expect(request.handler).toHaveBeenCalledOnce();
  });

  it("handles balance 401 when the request belongs to the current session", async () => {
    writePortalToken("client:a");
    const request = unauthorizedRequest("/api/common/points/balance");
    await request.ready;
    request.finish();
    await request.pending;
    expect(request.handler).toHaveBeenCalledOnce();
  });

  it("continues to skip credential failures on login", async () => {
    writePortalToken("client:a");
    const request = unauthorizedRequest("/api/common/auth/login");
    await request.ready;
    request.finish();
    await request.pending;
    expect(request.handler).not.toHaveBeenCalled();
  });
});
