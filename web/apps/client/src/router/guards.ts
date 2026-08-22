export const PUBLIC_PATHS = new Set(["/login", "/register", "/home", "/activity"]);
export const LOGIN_ROUTE = "/login";
export const HOME_ROUTE = "/home";
export const PASSWORD_ROUTE = "/mine/password";

export type GuardTo = {
  path: string;
  fullPath: string;
};

export type GuardDecision =
  | { type: "next" }
  | { type: "redirect"; path: string; query?: Record<string, string> };

export type GuardDeps = {
  sessionKnown: boolean;
  mustChangePassword?: boolean;
  ensureSession: () => Promise<boolean>;
};

export function isPublicPath(path: string): boolean {
  return PUBLIC_PATHS.has(path);
}

export async function resolveAuthNavigation(to: GuardTo, deps: GuardDeps): Promise<GuardDecision> {
  const mustChange = Boolean(deps.mustChangePassword);
  if (isPublicPath(to.path)) {
    if (to.path === LOGIN_ROUTE || to.path === "/register") {
      const ok = deps.sessionKnown ? true : await deps.ensureSession();
      if (ok) {
        return { type: "redirect", path: mustChange ? PASSWORD_ROUTE : HOME_ROUTE };
      }
    }
    return { type: "next" };
  }
  if (deps.sessionKnown) {
    if (mustChange && to.path !== PASSWORD_ROUTE) {
      return { type: "redirect", path: PASSWORD_ROUTE };
    }
    return { type: "next" };
  }
  const ok = await deps.ensureSession();
  if (!ok) {
    return { type: "redirect", path: LOGIN_ROUTE, query: { redirect: to.fullPath } };
  }
  if (mustChange && to.path !== PASSWORD_ROUTE) {
    return { type: "redirect", path: PASSWORD_ROUTE };
  }
  return { type: "next" };
}

export function safeRedirect(raw: unknown, fallback = HOME_ROUTE): string {
  return typeof raw === "string" && raw.startsWith("/") && !raw.startsWith("//") ? raw : fallback;
}

export function loginLocation(fromFullPath: string): { path: string; query: Record<string, string> } {
  return { path: LOGIN_ROUTE, query: { redirect: fromFullPath } };
}
