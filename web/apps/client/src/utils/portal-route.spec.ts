import { describe, expect, it } from "vitest";
import { resolvePortalRoute } from "./portal-route";

describe("resolvePortalRoute", () => {
  it("maps portal_route dictionary values to H5 paths", () => {
    expect(resolvePortalRoute("home")).toBe("/home");
    expect(resolvePortalRoute("mine")).toBe("/mine");
    expect(resolvePortalRoute("task-detail", { taskId: 9 })).toBe("/task/9");
    expect(resolvePortalRoute("task-detail", undefined, 3)).toBe("/task/3");
    expect(resolvePortalRoute("prize-list")).toBe("/mine/prizes");
    expect(resolvePortalRoute("points")).toBe("/mine/points");
    expect(resolvePortalRoute("password")).toBe("/mine/password");
    expect(resolvePortalRoute("signin")).toBeUndefined();
    expect(resolvePortalRoute("unknown")).toBeUndefined();
  });
});
