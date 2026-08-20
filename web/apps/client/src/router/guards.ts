export const PUBLIC_PATHS = new Set(["/login", "/register"]);
export const LOGIN_ROUTE = "/login";
export const HOME_ROUTE = "/home";

export type GuardTo = {
  path: string;
  fullPath: string;
};

export type GuardDecision =
  | { type: "next" }
  | { type: "redirect"; path: string; query?: Record<string, string> };

export type GuardDeps = {
  sessionKnown: boolean;
  ensureSession: () => Promise<boolean>;
};

export function isPublicPath(path: string): boolean {
  return PUBLIC_PATHS.has(path);
}

export async function resolveAuthNavigation(to: GuardTo, deps: GuardDeps): Promise<GuardDecision> {
  if (isPublicPath(to.path)) {
    if (to.path === LOGIN_ROUTE || to.path === "/register") {
      const ok = deps.sessionKnown ? true : await deps.ensureSession();
      if (ok) {
        return { type: "redirect", path: HOME_ROUTE };
      }
    }
    return { type: "next" };
  }
  if (deps.sessionKnown) {
    return { type: "next" };
  }
  const ok = await deps.ensureSession();
  if (!ok) {
    return { type: "redirect", path: LOGIN_ROUTE, query: { redirect: to.fullPath } };
  }
  return { type: "next" };
}

export function safeRedirect(raw: unknown, fallback = HOME_ROUTE): string {
  return typeof raw === "string" && raw.startsWith("/") && !raw.startsWith("//") ? raw : fallback;
}
