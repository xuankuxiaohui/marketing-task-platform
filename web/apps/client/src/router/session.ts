import type { Router } from "vue-router";
import { isOk } from "@mkt/shared";
import { fetchProfile, logout } from "@/api/auth";
import { HOME_ROUTE, LOGIN_ROUTE } from "@/router/guards";
import { useSessionStore } from "@/store/session";

export async function ensurePortalSession(): Promise<boolean> {
  const session = useSessionStore();
  const token = session.token;
  if (!token) {
    return false;
  }
  if (session.userId != null) {
    return true;
  }
  const currentSessionKnown = () => session.authenticated && session.userId != null;
  try {
    const profile = await fetchProfile();
    if (session.token !== token) {
      return currentSessionKnown();
    }
    if (!isOk(profile) || !profile.data) {
      session.clear();
      return false;
    }
    session.setProfile(profile.data);
    return true;
  } catch {
    if (session.token !== token) {
      return currentSessionKnown();
    }
    session.clear();
    return false;
  }
}

export function resetPortalSession(): void {
  useSessionStore().clear();
}

export async function logoutAndReset(current: Router): Promise<void> {
  const session = useSessionStore();
  const token = session.token;
  try {
    await logout();
  } finally {
    if (session.token === token) {
      resetPortalSession();
      if (current.currentRoute.value.path !== LOGIN_ROUTE) {
        await current.replace(LOGIN_ROUTE);
      }
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
