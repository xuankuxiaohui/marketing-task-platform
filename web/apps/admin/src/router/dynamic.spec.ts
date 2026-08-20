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
    expect(viewModuleKey("system/user/index", keys)).toBeUndefined();
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
