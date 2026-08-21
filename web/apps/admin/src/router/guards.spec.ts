import { describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
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

  it("re-resolves the original path after installing dynamic routes", async () => {
    const ensureSession = vi.fn().mockResolvedValue(true);
    const decision = await resolveAuthNavigation(
      { path: "/task/definitions", fullPath: "/task/definitions" },
      { routesReady: false, sessionKnown: false, ensureSession },
    );
    expect(ensureSession).toHaveBeenCalledTimes(1);
    expect(decision).toEqual({ type: "replace", path: "/task/definitions" });
  });

  it("matches a freshly added child route only after replace", async () => {
    const Page = { template: "<div />" };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: "/",
          name: "AdminRoot",
          component: { template: "<router-view />" },
          children: [],
        },
      ],
    });
    let installed = false;
    router.beforeEach((to) => {
      if (installed) {
        return true;
      }
      router.addRoute("AdminRoot", { path: "/task/definitions", name: "task-defs", component: Page });
      installed = true;
      return { path: to.path, replace: true };
    });
    await router.push("/task/definitions");
    await router.isReady();
    expect(router.currentRoute.value.name).toBe("task-defs");
  });
});
