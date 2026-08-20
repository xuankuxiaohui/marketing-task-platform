export function resolvePortalRoute(
  route: string,
  params?: Record<string, unknown>,
  fallbackTaskId?: number,
): string | undefined {
  switch (route) {
    case "home":
      return "/home";
    case "mine":
      return "/mine";
    case "task-detail": {
      const raw = params?.taskId ?? fallbackTaskId;
      const id = typeof raw === "number" ? raw : typeof raw === "string" ? Number(raw) : NaN;
      return Number.isFinite(id) ? `/task/${id}` : undefined;
    }
    case "prize-list":
    case "prize-detail":
      return "/mine/prizes";
    case "points":
      return "/mine/points";
    case "password":
      return "/mine/password";
    case "signin":
      return undefined;
    default:
      return undefined;
  }
}
