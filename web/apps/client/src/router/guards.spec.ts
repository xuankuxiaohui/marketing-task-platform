import { describe, expect, it, vi } from "vitest";
import { isPublicPath, resolveAuthNavigation, safeRedirect } from "./guards";

describe("portal route guards", () => {
  it("treats login, register, home and activity as public", () => {
    expect(isPublicPath("/login")).toBe(true);
    expect(isPublicPath("/register")).toBe(true);
    expect(isPublicPath("/home")).toBe(true);
    expect(isPublicPath("/activity")).toBe(true);
    expect(isPublicPath("/activities")).toBe(true);
    expect(isPublicPath("/mine")).toBe(false);
    expect(isPublicPath("/signin")).toBe(false);
  });

  it("allows anonymous users through the activity hub without probing session", async () => {
    const ensureSession = vi.fn().mockResolvedValue(false);
    const home = await resolveAuthNavigation(
      { path: "/home", fullPath: "/home" },
      { sessionKnown: false, ensureSession },
    );
    const activity = await resolveAuthNavigation(
      { path: "/activity", fullPath: "/activity?id=3" },
      { sessionKnown: false, ensureSession },
    );
    expect(ensureSession).not.toHaveBeenCalled();
    expect(home).toEqual({ type: "next" });
    expect(activity).toEqual({ type: "next" });
  });

  it("keeps anonymous users on protected routes so overlay login can open", async () => {
    const ensureSession = vi.fn().mockResolvedValue(false);
    const decision = await resolveAuthNavigation(
      { path: "/mine", fullPath: "/mine" },
      { sessionKnown: false, ensureSession },
    );
    expect(ensureSession).toHaveBeenCalled();
    expect(decision).toEqual({ type: "next" });
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
