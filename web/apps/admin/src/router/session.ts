import type { Router } from "vue-router";
import { isOk } from "@mkt/shared";
import { fetchMenus, fetchProfile, logout } from "@/api/auth";
import { ADMIN_ROOT_NAME, LOGIN_ROUTE } from "@/router/dynamic";
import { usePermissionStore } from "@/store/permission";
import { useSessionStore } from "@/store/session";
import { useTagsStore } from "@/store/tags";

let installing = false;

export async function ensureDynamicRoutes(current: Router): Promise<boolean> {
  const permission = usePermissionStore();
  if (permission.ready) {
    return true;
  }
  if (installing) {
    return permission.ready;
  }
  installing = true;
  try {
    const profile = await fetchProfile();
    if (!isOk(profile) || !profile.data) {
      return false;
    }
    useSessionStore().setProfile(profile.data);
    const menus = await fetchMenus();
    if (!isOk(menus) || !menus.data) {
      return false;
    }
    const routes = permission.setMenus(menus.data);
    for (const route of routes) {
      if (route.name && current.hasRoute(route.name)) {
        continue;
      }
      current.addRoute(ADMIN_ROOT_NAME, route);
    }
    return true;
  } catch {
    return false;
  } finally {
    installing = false;
  }
}

export function resetClientSession(current: Router): void {
  useSessionStore().clear();
  usePermissionStore().reset();
  useTagsStore().reset();
  installing = false;
  const keep = new Set(["LoginPage", "ChangePasswordPage", ADMIN_ROOT_NAME]);
  for (const route of current.getRoutes()) {
    if (route.name && !keep.has(String(route.name))) {
      current.removeRoute(route.name);
    }
  }
}

export async function logoutAndReset(current: Router): Promise<void> {
  try {
    await logout();
  } finally {
    resetClientSession(current);
    if (current.currentRoute.value.path !== LOGIN_ROUTE) {
      await current.replace(LOGIN_ROUTE);
    }
  }
}
