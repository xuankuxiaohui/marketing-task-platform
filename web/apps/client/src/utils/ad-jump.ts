import { resolvePortalRoute } from "./portal-route";

export type AdJump = {
  jumpType?: string | null;
  jumpParams?: Record<string, unknown> | null;
};

export function resolveAdJump(material: AdJump): { kind: "route" | "link" | "scheme"; target: string } | undefined {
  const type = (material.jumpType ?? "NONE").toUpperCase();
  const params = material.jumpParams ?? {};
  if (type === "NONE") {
    return undefined;
  }
  if (type === "LINK") {
    const url = typeof params.url === "string" ? params.url : "";
    if (!url.toLowerCase().startsWith("https://")) {
      return undefined;
    }
    return { kind: "link", target: url };
  }
  if (type === "SCHEME") {
    const scheme = typeof params.scheme === "string" ? params.scheme : "";
    return scheme ? { kind: "scheme", target: scheme } : undefined;
  }
  if (type === "ROUTE") {
    const route = typeof params.route === "string" ? params.route : "";
    const path = resolvePortalRoute(route, params);
    return path ? { kind: "route", target: path } : undefined;
  }
  return undefined;
}
