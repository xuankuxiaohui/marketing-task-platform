import { describe, expect, it } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import type { AdminMenuNode } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import {
  MENU_SEED_COMPONENTS,
  firstAuthorizedPath,
  groupSidebarMenus,
  isLiteralRouteParam,
  isParamRoute,
  menusToRoutes,
  sidebarMenus,
  viewModuleKey,
} from "./dynamic";

function menu(partial: Partial<AdminMenuNode> & Pick<AdminMenuNode, "id" | "name" | "route" | "component">): AdminMenuNode {
  return {
    sort: 1,
    icon: undefined,
    children: [],
    ...partial,
  };
}

describe("dynamic routes from §4.10 menus", () => {
  it("drops login from sidebar and route table", () => {
    const nodes = [
      menu({ id: 1, name: "登录", route: "/login", component: "login/index" }),
      menu({ id: 2, name: "工作台", route: "/dashboard", component: "dashboard/index" }),
      menu({ id: 3, name: "后台用户", route: "/system/users", component: "system/user/index" }),
    ];
    expect(sidebarMenus(nodes).map((n) => n.route)).toEqual(["/dashboard", "/system/users"]);
    expect(menusToRoutes(nodes).map((r) => r.path)).toEqual(["/dashboard", "/system/users"]);
  });

  it("resolves dashboard view and falls back for later pages", () => {
    const keys = ["../views/login/index.vue", "../views/dashboard/index.vue"];
    expect(viewModuleKey("dashboard/index", keys)).toBe("../views/dashboard/index.vue");
    expect(viewModuleKey("task/definition/index", keys)).toBeUndefined();
  });

  it("resolves §4.10 system management views", () => {
    const keys = [
      "../views/system/user/index.vue",
      "../views/system/role/index.vue",
      "../views/system/session/index.vue",
      "../views/system/portal-user/index.vue",
      "../views/system/internal-app/index.vue",
      "../views/system/dict/index.vue",
      "../views/system/config/index.vue",
      "../views/system/cache/index.vue",
      "../views/system/audit/index.vue",
    ];
    expect(viewModuleKey("system/user/index", keys)).toBe("../views/system/user/index.vue");
    expect(viewModuleKey("system/audit/index", keys)).toBe("../views/system/audit/index.vue");
  });

  it("resolves §4.10 task and reward views", () => {
    const keys = [
      "../views/task/definition/index.vue",
      "../views/task/definition/edit.vue",
      "../views/task/definition/version.vue",
      "../views/task/mutex-group/index.vue",
      "../views/task/crowd/index.vue",
      "../views/task/instance/index.vue",
      "../views/reward/category/index.vue",
      "../views/reward/prize/index.vue",
      "../views/reward/record/index.vue",
      "../views/reward/recon/index.vue",
      "../views/points/account/index.vue",
      "../views/points/transaction/index.vue",
    ];
    expect(viewModuleKey("task/definition/index", keys)).toBe("../views/task/definition/index.vue");
    expect(viewModuleKey("task/definition/edit", keys)).toBe("../views/task/definition/edit.vue");
    expect(viewModuleKey("reward/prize/index", keys)).toBe("../views/reward/prize/index.vue");
    expect(viewModuleKey("points/account/index", keys)).toBe("../views/points/account/index.vue");
  });

  it("resolves §4.10 risk and track views", () => {
    const keys = [
      "../views/risk/list-item/index.vue",
      "../views/risk/rule/index.vue",
      "../views/risk/case/index.vue",
      "../views/track/metadata/index.vue",
      "../views/track/event/index.vue",
    ];
    expect(viewModuleKey("risk/list-item/index", keys)).toBe("../views/risk/list-item/index.vue");
    expect(viewModuleKey("risk/rule/index", keys)).toBe("../views/risk/rule/index.vue");
    expect(viewModuleKey("risk/case/index", keys)).toBe("../views/risk/case/index.vue");
    expect(viewModuleKey("track/metadata/index", keys)).toBe("../views/track/metadata/index.vue");
    expect(viewModuleKey("track/event/index", keys)).toBe("../views/track/event/index.vue");
  });

  it("resolves signin views", () => {
    const keys = ["../views/signin/activity/index.vue", "../views/signin/record/index.vue"];
    expect(viewModuleKey("signin/activity/index", keys)).toBe("../views/signin/activity/index.vue");
    expect(viewModuleKey("signin/record/index", keys)).toBe("../views/signin/record/index.vue");
  });

  it("resolves metrics view", () => {
    const keys = ["../views/metrics/index.vue"];
    expect(viewModuleKey("metrics/index", keys)).toBe("../views/metrics/index.vue");
  });

  it("resolves simulate view", () => {
    const keys = ["../views/simulate/index.vue"];
    expect(viewModuleKey("simulate/index", keys)).toBe("../views/simulate/index.vue");
  });

  it("covers every §4.10 component path plus P1 signin metrics and simulate", () => {
    expect(MENU_SEED_COMPONENTS).toHaveLength(32);
    expect(MENU_SEED_COMPONENTS[0]).toBe("login/index");
    expect(MENU_SEED_COMPONENTS[1]).toBe("dashboard/index");
    expect(firstAuthorizedPath([menu({ id: 2, name: "工作台", route: "/dashboard", component: "dashboard/index" })])).toBe(
      "/dashboard",
    );
  });

  it("detects Vue-router param segments", () => {
    expect(isParamRoute("/task/definitions/edit/:id?")).toBe(true);
    expect(isParamRoute("/task/definitions/:id/versions")).toBe(true);
    expect(isParamRoute("/task/definitions")).toBe(false);
    expect(isParamRoute("/login")).toBe(false);
    expect(isLiteralRouteParam(":id")).toBe(true);
    expect(isLiteralRouteParam(":id?")).toBe(true);
    expect(isLiteralRouteParam("8")).toBe(false);
  });

  it("hides seed :id menus from sidebar and landing path but keeps them as routes", () => {
    const nodes = [
      menu({ id: 1, name: "登录", route: "/login", component: "login/index" }),
      menu({ id: 12, name: "任务列表", route: "/task/definitions", component: "task/definition/index" }),
      menu({
        id: 13,
        name: "任务编辑（画布）",
        route: "/task/definitions/edit/:id?",
        component: "task/definition/edit",
      }),
      menu({
        id: 14,
        name: "任务版本",
        route: "/task/definitions/:id/versions",
        component: "task/definition/version",
      }),
    ];
    expect(sidebarMenus(nodes).map((n) => n.route)).toEqual(["/task/definitions"]);
    expect(firstAuthorizedPath(nodes)).toBe("/task/definitions");
    expect(menusToRoutes(nodes).map((r) => r.path)).toEqual([
      "/task/definitions",
      "/task/definitions/edit/:id?",
      "/task/definitions/:id/versions",
    ]);
  });

  it("redirects bookmark-style literal :id params to the task list", async () => {
    const routes = menusToRoutes([
      menu({ id: 12, name: "任务列表", route: "/task/definitions", component: "task/definition/index" }),
      menu({
        id: 13,
        name: "任务编辑（画布）",
        route: "/task/definitions/edit/:id?",
        component: "task/definition/edit",
      }),
      menu({
        id: 14,
        name: "任务版本",
        route: "/task/definitions/:id/versions",
        component: "task/definition/version",
      }),
    ]);
    const router = createRouter({ history: createMemoryHistory(), routes });
    await router.push("/task/definitions/edit/:id");
    await router.isReady();
    expect(router.currentRoute.value.path).toBe("/task/definitions");
    await router.push("/task/definitions/:id/versions");
    expect(router.currentRoute.value.path).toBe("/task/definitions");
  });

  it("groups sidebar menus by prefix with zh-CN titles and hides :id items", () => {
    const nodes = [
      menu({ id: 1, name: "登录", route: "/login", component: "login/index" }),
      menu({ id: 2, name: "工作台", route: "/dashboard", component: "dashboard/index" }),
      menu({ id: 3, name: "后台用户", route: "/system/users", component: "system/user/index" }),
      menu({ id: 4, name: "角色权限", route: "/system/roles", component: "system/role/index" }),
      menu({ id: 12, name: "任务列表", route: "/task/definitions", component: "task/definition/index" }),
      menu({
        id: 13,
        name: "任务编辑（画布）",
        route: "/task/definitions/edit/:id?",
        component: "task/definition/edit",
      }),
      menu({ id: 20, name: "奖品管理", route: "/reward/prizes", component: "reward/prize/index" }),
      menu({ id: 30, name: "积分账户", route: "/points/accounts", component: "points/account/index" }),
      menu({ id: 40, name: "风控名单", route: "/risk/list-items", component: "risk/list-item/index" }),
      menu({ id: 50, name: "埋点元数据", route: "/track/metadata", component: "track/metadata/index" }),
      menu({ id: 60, name: "签到活动", route: "/signin/activities", component: "signin/activity/index" }),
      menu({ id: 70, name: "活动管理", route: "/activity/list", component: "activity/index" }),
      menu({ id: 80, name: "广告位", route: "/ad/positions", component: "ad/position/index" }),
      menu({ id: 90, name: "运营看板", route: "/metrics", component: "metrics/index" }),
      menu({ id: 91, name: "任务模拟器", route: "/simulate", component: "simulate/index" }),
    ];
    const groups = groupSidebarMenus(nodes);
    expect(groups.map((group) => [group.key, group.title, group.items.map((item) => item.route)])).toEqual([
      ["dashboard", zhCN.menuGroup.dashboard, ["/dashboard"]],
      ["system", zhCN.menuGroup.system, ["/system/users", "/system/roles"]],
      ["task", zhCN.menuGroup.task, ["/task/definitions"]],
      ["reward", zhCN.menuGroup.reward, ["/reward/prizes"]],
      ["points", zhCN.menuGroup.points, ["/points/accounts"]],
      ["risk", zhCN.menuGroup.risk, ["/risk/list-items"]],
      ["track", zhCN.menuGroup.track, ["/track/metadata"]],
      ["signin", zhCN.menuGroup.signin, ["/signin/activities"]],
      ["activity", zhCN.menuGroup.activity, ["/activity/list"]],
      ["ad", zhCN.menuGroup.ad, ["/ad/positions"]],
      ["other", zhCN.menuGroup.other, ["/metrics", "/simulate"]],
    ]);
    expect(groups.flatMap((group) => group.items.map((item) => item.route))).not.toContain("/login");
    expect(groups.flatMap((group) => group.items.map((item) => item.route))).not.toContain(
      "/task/definitions/edit/:id?",
    );
  });

  it("omits empty sidebar groups", () => {
    const groups = groupSidebarMenus([
      menu({ id: 2, name: "工作台", route: "/dashboard", component: "dashboard/index" }),
      menu({ id: 90, name: "运营看板", route: "/metrics", component: "metrics/index" }),
    ]);
    expect(groups.map((group) => group.key)).toEqual(["dashboard", "other"]);
    expect(groups.map((group) => group.title)).toEqual([
      zhCN.menuGroup.dashboard,
      zhCN.menuGroup.other,
    ]);
  });
});
