import { CHANGE_PASSWORD_ROUTE, DASHBOARD_ROUTE, LOGIN_ROUTE } from "@/router/dynamic";

export const PUBLIC_PATHS = new Set([LOGIN_ROUTE]);

export type GuardTo = {
  path: string;
  fullPath: string;
};

export type GuardDecision =
  | { type: "next" }
  | { type: "replace"; path: string }
  | { type: "redirect"; path: string; query?: Record<string, string> };

export type GuardDeps = {
  routesReady: boolean;
  sessionKnown: boolean;
  mustChangePassword?: boolean;
  ensureSession: () => Promise<boolean>;
};

export function isPublicPath(path: string): boolean {
  return PUBLIC_PATHS.has(path);
}

/**
 * Auth navigation for vue-pure-admin-thin style dynamic routes.
 * Session token stays in HttpOnly cookie; this only decides next/redirect/replace.
 * After addRoute, Vue Router must re-resolve the original location (replace),
 * otherwise a refresh of /task/definitions stays unmatched.
 */
export async function resolveAuthNavigation(to: GuardTo, deps: GuardDeps): Promise<GuardDecision> {
  const mustChange = Boolean(deps.mustChangePassword);
  if (isPublicPath(to.path)) {
    if (to.path === LOGIN_ROUTE && (deps.routesReady || deps.sessionKnown)) {
      const ok = deps.routesReady ? true : await deps.ensureSession();
      if (ok) {
        return {
          type: "redirect",
          path: mustChange ? CHANGE_PASSWORD_ROUTE : DASHBOARD_ROUTE,
        };
      }
    }
    return { type: "next" };
  }
  if (to.path === CHANGE_PASSWORD_ROUTE) {
    if (deps.routesReady || deps.sessionKnown) {
      const ok = deps.routesReady ? true : await deps.ensureSession();
      if (!ok) {
        return { type: "redirect", path: LOGIN_ROUTE };
      }
      return { type: "next" };
    }
    const ok = await deps.ensureSession();
    if (!ok) {
      return { type: "redirect", path: LOGIN_ROUTE };
    }
    return { type: "next" };
  }
  if (deps.routesReady) {
    if (mustChange) {
      return { type: "redirect", path: CHANGE_PASSWORD_ROUTE };
    }
    return { type: "next" };
  }
  const ok = await deps.ensureSession();
  if (!ok) {
    return { type: "redirect", path: LOGIN_ROUTE, query: { redirect: to.fullPath } };
  }
  if (mustChange) {
    return { type: "redirect", path: CHANGE_PASSWORD_ROUTE };
  }
  return { type: "replace", path: to.path };
}
