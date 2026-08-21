export const PUBLIC_PATHS = new Set(["/login"]);

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
  if (isPublicPath(to.path)) {
    if (to.path === "/login" && (deps.routesReady || deps.sessionKnown)) {
      const ok = deps.routesReady ? true : await deps.ensureSession();
      if (ok) {
        return { type: "redirect", path: "/dashboard" };
      }
    }
    return { type: "next" };
  }
  if (deps.routesReady) {
    return { type: "next" };
  }
  const ok = await deps.ensureSession();
  if (!ok) {
    return { type: "redirect", path: "/login", query: { redirect: to.fullPath } };
  }
  return { type: "replace", path: to.path };
}
