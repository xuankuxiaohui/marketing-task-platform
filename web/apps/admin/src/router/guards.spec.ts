import { describe, expect, it, vi } from "vitest";
import { isPublicPath, resolveAuthNavigation } from "./guards";

describe("route guards", () => {
  it("treats login as public", () => {
    expect(isPublicPath("/login")).toBe(true);
    expect(isPublicPath("/dashboard")).toBe(false);
  });

  it("sends anonymous users from protected routes to login with redirect", async () => {
    const ensureSession = vi.fn().mockResolvedValue(false);
    const decision = await resolveAuthNavigation(
      { path: "/dashboard", fullPath: "/dashboard" },
      { routesReady: false, sessionKnown: false, ensureSession },
    );
    expect(ensureSession).toHaveBeenCalled();
    expect(decision).toEqual({
      type: "redirect",
      path: "/login",
      query: { redirect: "/dashboard" },
    });
  });

  it("allows public login without probing session when unknown", async () => {
    const ensureSession = vi.fn();
    const decision = await resolveAuthNavigation(
      { path: "/login", fullPath: "/login" },
      { routesReady: false, sessionKnown: false, ensureSession },
    );
    expect(ensureSession).not.toHaveBeenCalled();
    expect(decision).toEqual({ type: "next" });
  });

  it("redirects authenticated users away from login", async () => {
    const ensureSession = vi.fn().mockResolvedValue(true);
    const decision = await resolveAuthNavigation(
      { path: "/login", fullPath: "/login" },
      { routesReady: false, sessionKnown: true, ensureSession },
    );
    expect(decision).toEqual({ type: "redirect", path: "/dashboard" });
  });

  it("lets ready sessions through protected routes", async () => {
    const ensureSession = vi.fn();
    const decision = await resolveAuthNavigation(
      { path: "/system/users", fullPath: "/system/users" },
      { routesReady: true, sessionKnown: true, ensureSession },
    );
    expect(ensureSession).not.toHaveBeenCalled();
    expect(decision).toEqual({ type: "next" });
  });

  it("installs dynamic routes once for a valid cookie session", async () => {
    const ensureSession = vi.fn().mockResolvedValue(true);
    const decision = await resolveAuthNavigation(
      { path: "/task/definitions", fullPath: "/task/definitions" },
      { routesReady: false, sessionKnown: false, ensureSession },
    );
    expect(ensureSession).toHaveBeenCalledTimes(1);
    expect(decision).toEqual({ type: "next" });
  });
});
