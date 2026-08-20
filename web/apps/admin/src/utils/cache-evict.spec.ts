import { describe, expect, it } from "vitest";
import { buildCacheEvictCommand, SESSION_NAMESPACE } from "./cache-evict";

describe("buildCacheEvictCommand R9.2", () => {
  it("rejects identity:session at any granularity", () => {
    expect(buildCacheEvictCommand({ level: "NAMESPACE", namespace: SESSION_NAMESPACE })).toEqual({
      ok: false,
      reason: "session",
    });
    expect(buildCacheEvictCommand({ level: "KEY", namespace: "dict", key: "identity:session:abc" })).toEqual({
      ok: false,
      reason: "session",
    });
    expect(buildCacheEvictCommand({ level: "PREFIX", namespace: "config", prefix: "identity:session:" })).toEqual({
      ok: false,
      reason: "session",
    });
  });

  it("requires namespace+key for KEY and drops extras", () => {
    expect(buildCacheEvictCommand({ level: "KEY", namespace: "dict", key: "province" })).toEqual({
      ok: true,
      body: { level: "KEY", namespace: "dict", key: "province" },
    });
    expect(buildCacheEvictCommand({ level: "KEY", namespace: "dict", key: "province", prefix: "p" }).ok).toBe(false);
  });

  it("requires namespace+prefix for PREFIX", () => {
    expect(buildCacheEvictCommand({ level: "PREFIX", namespace: "dict", prefix: "pro" })).toEqual({
      ok: true,
      body: { level: "PREFIX", namespace: "dict", prefix: "pro" },
    });
  });

  it("requires only namespace for NAMESPACE", () => {
    expect(buildCacheEvictCommand({ level: "NAMESPACE", namespace: "config" })).toEqual({
      ok: true,
      body: { level: "NAMESPACE", namespace: "config" },
    });
    expect(buildCacheEvictCommand({ level: "NAMESPACE", namespace: "config", key: "x" }).ok).toBe(false);
  });
});
