/** R9.1 / R9.2: session namespace must not be evicted. */
export const SESSION_NAMESPACE = "identity:session";

export const CACHE_EVICT_LEVELS = ["KEY", "PREFIX", "NAMESPACE"] as const;
export type CacheEvictLevel = (typeof CACHE_EVICT_LEVELS)[number];

export type CacheEvictInput = {
  level: string;
  namespace?: string;
  prefix?: string;
  key?: string;
};

export type CacheEvictCommandBody = {
  level: CacheEvictLevel;
  namespace: string;
  prefix?: string;
  key?: string;
};

function blankToUndefined(value: string | undefined): string | undefined {
  if (value == null) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
}

export function inSessionSpace(value: string | undefined): boolean {
  if (!value) {
    return false;
  }
  return value === SESSION_NAMESPACE || value.startsWith(`${SESSION_NAMESPACE}:`);
}

export function isSessionEvictForbidden(input: Pick<CacheEvictInput, "namespace" | "prefix" | "key">): boolean {
  return inSessionSpace(input.namespace) || inSessionSpace(input.prefix) || inSessionSpace(input.key);
}

export function buildCacheEvictCommand(
  input: CacheEvictInput,
): { ok: true; body: CacheEvictCommandBody } | { ok: false; reason: "session" | "param" } {
  if (isSessionEvictForbidden(input)) {
    return { ok: false, reason: "session" };
  }
  const namespace = blankToUndefined(input.namespace);
  const prefix = blankToUndefined(input.prefix);
  const key = blankToUndefined(input.key);
  if (input.level === "KEY") {
    if (!namespace || !key || prefix) {
      return { ok: false, reason: "param" };
    }
    return { ok: true, body: { level: "KEY", namespace, key } };
  }
  if (input.level === "PREFIX") {
    if (!namespace || !prefix || key) {
      return { ok: false, reason: "param" };
    }
    return { ok: true, body: { level: "PREFIX", namespace, prefix } };
  }
  if (input.level === "NAMESPACE") {
    if (!namespace || prefix || key) {
      return { ok: false, reason: "param" };
    }
    return { ok: true, body: { level: "NAMESPACE", namespace } };
  }
  return { ok: false, reason: "param" };
}
