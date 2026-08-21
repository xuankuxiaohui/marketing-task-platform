import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import { DASHBOARD_ROUTE } from "@/router/dynamic";
import { useTagsStore } from "@/store/tags";

describe("useTagsStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  function seed() {
    const tags = useTagsStore();
    tags.push({ path: DASHBOARD_ROUTE, title: "工作台" });
    tags.push({ path: "/system/users", title: "后台用户" });
    tags.push({ path: "/task/definitions", title: "任务列表" });
    tags.push({ path: "/metrics", title: "运营看板" });
    return tags;
  }

  it("pins dashboard on push and skips duplicates", () => {
    const tags = seed();
    tags.push({ path: DASHBOARD_ROUTE, title: "工作台" });
    expect(tags.items.map((item) => item.path)).toEqual([
      DASHBOARD_ROUTE,
      "/system/users",
      "/task/definitions",
      "/metrics",
    ]);
    expect(tags.items[0]?.pinned).toBe(true);
  });

  it("close removes only the current path including dashboard", () => {
    const tags = seed();
    tags.close(DASHBOARD_ROUTE);
    expect(tags.items.map((item) => item.path)).toEqual([
      "/system/users",
      "/task/definitions",
      "/metrics",
    ]);
  });

  it("closeOthers keeps the target and pinned dashboard", () => {
    const tags = seed();
    tags.closeOthers("/task/definitions");
    expect(tags.items.map((item) => item.path)).toEqual([DASHBOARD_ROUTE, "/task/definitions"]);
  });

  it("closeLeft keeps pinned dashboard even when it is on the left", () => {
    const tags = seed();
    tags.closeLeft("/task/definitions");
    expect(tags.items.map((item) => item.path)).toEqual([
      DASHBOARD_ROUTE,
      "/task/definitions",
      "/metrics",
    ]);
  });

  it("closeRight keeps tags at and left of the target", () => {
    const tags = seed();
    tags.closeRight("/system/users");
    expect(tags.items.map((item) => item.path)).toEqual([DASHBOARD_ROUTE, "/system/users"]);
  });
});
