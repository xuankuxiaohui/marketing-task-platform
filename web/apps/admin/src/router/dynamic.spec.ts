import { describe, expect, it } from "vitest";
import type { AdminMenuNode } from "@/api/auth";
import {
  MENU_SEED_COMPONENTS,
  firstAuthorizedPath,
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

  it("covers every §4.10 component path", () => {
    expect(MENU_SEED_COMPONENTS).toHaveLength(28);
    expect(MENU_SEED_COMPONENTS[0]).toBe("login/index");
    expect(MENU_SEED_COMPONENTS[1]).toBe("dashboard/index");
    expect(firstAuthorizedPath([menu({ id: 2, name: "工作台", route: "/dashboard", component: "dashboard/index" })])).toBe(
      "/dashboard",
    );
  });
});
