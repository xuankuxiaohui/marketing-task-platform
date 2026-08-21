import { describe, expect, it, vi } from "vitest";
import { isPublicPath, resolveAuthNavigation, safeRedirect } from "./guards";

describe("portal route guards", () => {
  it("treats login and register as public", () => {
    expect(isPublicPath("/login")).toBe(true);
    expect(isPublicPath("/register")).toBe(true);
    expect(isPublicPath("/mine")).toBe(false);
  });

  it("sends anonymous users from protected routes to login with redirect", async () => {
    const ensureSession = vi.fn().mockResolvedValue(false);
    const decision = await resolveAuthNavigation(
      { path: "/mine", fullPath: "/mine" },
      { sessionKnown: false, ensureSession },
    );
    expect(ensureSession).toHaveBeenCalled();
    expect(decision).toEqual({ type: "redirect", path: "/login", query: { redirect: "/mine" } });
  });

  it("allows public login without probing when session is known missing", async () => {
    const ensureSession = vi.fn().mockResolvedValue(false);
    const decision = await resolveAuthNavigation(
      { path: "/login", fullPath: "/login" },
      { sessionKnown: false, ensureSession },
    );
    expect(decision).toEqual({ type: "next" });
  });

  it("redirects authenticated users away from login", async () => {
    const decision = await resolveAuthNavigation(
      { path: "/login", fullPath: "/login" },
      { sessionKnown: true, ensureSession: vi.fn() },
    );
    expect(decision).toEqual({ type: "redirect", path: "/home" });
  });

  it("keeps only in-app redirect paths", () => {
    expect(safeRedirect("/mine/profile")).toBe("/mine/profile");
    expect(safeRedirect("https://evil.example")).toBe("/home");
    expect(safeRedirect("//evil.example")).toBe("/home");
  });
});
