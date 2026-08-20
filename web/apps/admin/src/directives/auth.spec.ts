import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import { hasAuth } from "./auth";
import { useSessionStore } from "@/store/session";

describe("v-auth / hasAuth", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("checks backend permission codes", () => {
    const session = useSessionStore();
    session.permissions = ["identity:admin-user:query", "identity:admin-user:create"];
    expect(hasAuth("identity:admin-user:create")).toBe(true);
    expect(hasAuth("identity:admin-user:delete")).toBe(false);
    expect(hasAuth(["identity:admin-user:query", "identity:admin-user:create"])).toBe(true);
    expect(hasAuth(["identity:admin-user:query", "identity:admin-user:delete"])).toBe(false);
  });
});
