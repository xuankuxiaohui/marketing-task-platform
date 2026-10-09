import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { PORTAL_TOKEN_KEY, readPortalToken, writePortalToken } from "@/utils/token";
import { useSessionStore } from "./session";

describe("portal session", () => {
  beforeEach(() => {
    writePortalToken("");
    setActivePinia(createPinia());
  });

  afterEach(() => {
    useSessionStore().$dispose();
    writePortalToken("");
  });

  function populatedSession() {
    const session = useSessionStore();
    session.setLogin({ token: "client:a", userId: 1, nickname: "旧账号" });
    session.setProfile({
      userId: 1,
      username: "alice",
      nickname: "旧账号",
      province: "浙江",
      userLevel: "VIP",
      userRole: "MEMBER",
      tags: ["old"],
      pointsBalance: 90,
      mustChangePassword: true,
    });
    return session;
  }

  it("clears the previous account profile and balance before a new login profile arrives", () => {
    const session = populatedSession();
    session.setLogin({ token: "client:b", userId: 2, nickname: "新账号" });
    expect(session.authenticated).toBe(true);
    expect(session.token).toBe("client:b");
    expect(readPortalToken()).toBe("client:b");
    expect(session.userId).toBe(2);
    expect(session.nickname).toBe("新账号");
    expect(session.username).toBe("");
    expect(session.province).toBe("");
    expect(session.userLevel).toBe("");
    expect(session.userRole).toBe("");
    expect(session.tags).toEqual([]);
    expect(session.pointsBalance).toBe(0);
    expect(session.mustChangePassword).toBe(false);
  });

  it("clears the profile when the same account starts a new session", () => {
    const session = populatedSession();
    session.setLogin({ token: "client:new", userId: 1, nickname: "刷新昵称" });
    expect(session.pointsBalance).toBe(0);
    expect(session.tags).toEqual([]);
    expect(session.username).toBe("");
    expect(session.nickname).toBe("刷新昵称");
  });

  it("clears old data when login supplies no user id or nickname", () => {
    const session = populatedSession();
    session.setLogin({ token: "client:b" });
    expect(session.userId).toBeNull();
    expect(session.nickname).toBe("");
    expect(session.pointsBalance).toBe(0);
    expect(session.tags).toEqual([]);
  });

  it("preserves the loaded profile when the same login result is applied twice", () => {
    const session = populatedSession();
    session.setLogin({ token: "client:a", userId: 1, nickname: "旧账号", mustChangePassword: true });
    expect(session.username).toBe("alice");
    expect(session.province).toBe("浙江");
    expect(session.tags).toEqual(["old"]);
    expect(session.pointsBalance).toBe(90);
    expect(session.mustChangePassword).toBe(true);
  });

  it("clears all account data and storage on logout", () => {
    const session = populatedSession();
    session.clear();
    expect(session.authenticated).toBe(false);
    expect(readPortalToken()).toBe("");
    expect(session.userId).toBeNull();
    expect(session.nickname).toBe("");
    expect(session.username).toBe("");
    expect(session.pointsBalance).toBe(0);
    expect(session.tags).toEqual([]);
    expect(session.mustChangePassword).toBe(false);
  });

  it("synchronously invalidates account data when another tab replaces the stored token", () => {
    const session = populatedSession();
    writePortalToken("client:b");
    window.dispatchEvent(new StorageEvent("storage", {
      key: PORTAL_TOKEN_KEY,
      oldValue: "client:a",
      newValue: "client:b",
      storageArea: localStorage,
    }));
    expect(session.token).toBe("client:b");
    expect(session.authenticated).toBe(true);
    expect(session.userId).toBeNull();
    expect(session.pointsBalance).toBe(0);
    expect(session.tags).toEqual([]);
  });

  it("handles storage clear and ignores unrelated or session-storage events", () => {
    const session = populatedSession();
    writePortalToken("client:b");
    window.dispatchEvent(new StorageEvent("storage", { key: "unrelated", storageArea: localStorage }));
    window.dispatchEvent(new StorageEvent("storage", { key: PORTAL_TOKEN_KEY, storageArea: sessionStorage }));
    expect(session.token).toBe("client:a");
    writePortalToken("");
    window.dispatchEvent(new StorageEvent("storage", { key: null, storageArea: localStorage }));
    expect(session.authenticated).toBe(false);
    expect(session.pointsBalance).toBe(0);
  });

  it("uses the latest stored token and stops listening after store disposal", () => {
    const session = populatedSession();
    writePortalToken("client:c");
    window.dispatchEvent(new StorageEvent("storage", {
      key: PORTAL_TOKEN_KEY,
      newValue: "client:b",
      storageArea: localStorage,
    }));
    expect(session.token).toBe("client:c");
    session.$dispose();
    writePortalToken("client:d");
    window.dispatchEvent(new StorageEvent("storage", { key: PORTAL_TOKEN_KEY, storageArea: localStorage }));
    expect(session.token).toBe("client:c");
  });
});
