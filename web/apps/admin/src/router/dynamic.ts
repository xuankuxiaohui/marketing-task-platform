import type { RouteRecordRaw } from "vue-router";
import type { AdminMenuNode } from "@/api/auth";
import ComingSoonPage from "@/views/placeholder/ComingSoonPage.vue";

const viewModules = import.meta.glob("../views/**/*.vue");

export const LOGIN_ROUTE = "/login";
export const CHANGE_PASSWORD_ROUTE = "/change-password";
export const DASHBOARD_ROUTE = "/dashboard";
export const ADMIN_ROOT_NAME = "AdminRoot";

export function viewModuleKey(component: string, keys: string[]): string | undefined {
  const needle = `/views/${component}.vue`;
  return keys.find((key) => key.replaceAll("\\", "/").endsWith(needle));
}

type ViewLoader = NonNullable<RouteRecordRaw["component"]>;

export function resolveView(component: string | undefined): ViewLoader {
  if (!component) {
    return ComingSoonPage;
  }
  const key = viewModuleKey(component, Object.keys(viewModules));
  const loader = key ? viewModules[key] : undefined;
  return (loader as ViewLoader | undefined) ?? ComingSoonPage;
}

export function isLoginMenu(node: Pick<AdminMenuNode, "route">): boolean {
  return node.route === LOGIN_ROUTE;
}

export function sidebarMenus(nodes: AdminMenuNode[]): AdminMenuNode[] {
  return nodes.filter((node) => !isLoginMenu(node));
}

export function menusToRoutes(nodes: AdminMenuNode[]): RouteRecordRaw[] {
  return flattenMenus(nodes)
    .filter((node) => Boolean(node.route) && !isLoginMenu(node))
    .map((node) => {
      const record: RouteRecordRaw = {
        path: node.route as string,
        name: `menu-${node.id}`,
        component: resolveView(node.component),
        meta: {
          title: node.name,
          icon: node.icon,
          sort: node.sort,
          component: node.component,
          backstage: true,
        },
      };
      return record;
    });
}

export function flattenMenus(nodes: AdminMenuNode[]): AdminMenuNode[] {
  const out: AdminMenuNode[] = [];
  const walk = (list: AdminMenuNode[]) => {
    for (const node of list) {
      out.push(node);
      if (node.children && node.children.length > 0) {
        walk(node.children);
      }
    }
  };
  walk(nodes);
  return out;
}

export function firstAuthorizedPath(nodes: AdminMenuNode[]): string {
  const first = flattenMenus(nodes).find((node) => node.route && !isLoginMenu(node));
  return first?.route ?? DASHBOARD_ROUTE;
}

/** design §4.10 menu component paths (V1 seed). */
export const MENU_SEED_COMPONENTS = [
  "login/index",
  "dashboard/index",
  "system/user/index",
  "system/role/index",
  "system/session/index",
  "system/portal-user/index",
  "system/internal-app/index",
  "system/dict/index",
  "system/config/index",
  "system/cache/index",
  "system/audit/index",
  "task/definition/index",
  "task/definition/edit",
  "task/definition/version",
  "task/mutex-group/index",
  "task/crowd/index",
  "task/instance/index",
  "reward/category/index",
  "reward/prize/index",
  "reward/record/index",
  "reward/recon/index",
  "points/account/index",
  "points/transaction/index",
  "risk/list-item/index",
  "risk/rule/index",
  "risk/case/index",
  "track/metadata/index",
  "track/event/index",
  "signin/activity/index",
  "signin/record/index",
  "metrics/index",
  "simulate/index",
] as const;
