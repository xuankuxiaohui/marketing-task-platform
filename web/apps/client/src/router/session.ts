import type { Router } from "vue-router";
import { isOk } from "@mkt/shared";
import { fetchProfile, logout } from "@/api/auth";
import { HOME_ROUTE, LOGIN_ROUTE } from "@/router/guards";
import { useSessionStore } from "@/store/session";

export async function ensurePortalSession(): Promise<boolean> {
  const session = useSessionStore();
  if (!session.token) {
    return false;
  }
  if (session.userId != null) {
    return true;
  }
  try {
    const profile = await fetchProfile();
    if (!isOk(profile) || !profile.data) {
      session.clear();
      return false;
    }
    session.setProfile(profile.data);
    return true;
  } catch {
    session.clear();
    return false;
  }
}

export function resetPortalSession(): void {
  useSessionStore().clear();
}

export async function logoutAndReset(current: Router): Promise<void> {
  try {
    await logout();
  } finally {
    resetPortalSession();
    if (current.currentRoute.value.path !== LOGIN_ROUTE) {
      await current.replace(LOGIN_ROUTE);
    }
  }
}

export function redirectAfterAuth(current: Router, rawRedirect: unknown): Promise<void> {
  const target =
    typeof rawRedirect === "string" && rawRedirect.startsWith("/") && !rawRedirect.startsWith("//")
      ? rawRedirect
      : HOME_ROUTE;
  return current.replace(target).then(() => undefined);
}
