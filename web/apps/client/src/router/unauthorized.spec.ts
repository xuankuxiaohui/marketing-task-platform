import { describe, expect, it, vi } from "vitest";
import { handlePortalUnauthorized, isKickSessionCode } from "./unauthorized";

describe("portal unauthorized handler", () => {
  it("opens overlay on a guest 401 without toasting or sending the host to /login", () => {
    const toast = vi.fn();
    const reset = vi.fn();
    const openOverlay = vi.fn();
    handlePortalUnauthorized(
      { code: "auth.session.missing", message: "请先登录" },
      { currentPath: "/home", reset, toast, openOverlay },
    );
    expect(reset).toHaveBeenCalled();
    expect(toast).not.toHaveBeenCalled();
    expect(openOverlay).toHaveBeenCalledWith({
      redirect: "/home",
      code: "auth.session.missing",
      message: undefined,
    });
  });

  it("toasts distinct kick copy and still overlays instead of replacing the route", () => {
    const toast = vi.fn();
    const openOverlay = vi.fn();
    handlePortalUnauthorized(
      { code: "auth.session.kicked-concurrent", message: "ignored" },
      { currentPath: "/mine/prizes", reset: vi.fn(), toast, openOverlay },
    );
    expect(isKickSessionCode("auth.session.kicked-concurrent")).toBe(true);
    expect(isKickSessionCode("auth.session.kicked-admin")).toBe(true);
    expect(isKickSessionCode("auth.session.missing")).toBe(false);
    expect(toast).toHaveBeenCalledWith("账号已在其他设备登录");
    expect(openOverlay).toHaveBeenCalledWith(
      expect.objectContaining({
        redirect: "/mine/prizes",
        code: "auth.session.kicked-concurrent",
        message: "账号已在其他设备登录",
      }),
    );
  });

  it("does not overlay when the user is already on the login page", () => {
    const openOverlay = vi.fn();
    handlePortalUnauthorized(
      { code: "auth.session.expired" },
      { currentPath: "/login", reset: vi.fn(), toast: vi.fn(), openOverlay },
    );
    expect(openOverlay).not.toHaveBeenCalled();
  });
});
