import { createPinia, disposePinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Result } from "@mkt/shared";
import type { PortalProfileData } from "@/api/auth";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";
import { readPortalToken, writePortalToken } from "@/utils/token";

vi.mock("@/api/auth", () => ({ fetchProfile: vi.fn(), logout: vi.fn() }));

import { fetchProfile, logout } from "@/api/auth";
import { ensurePortalSession, logoutAndReset, redirectAfterAuth, resetPortalSession } from "./session";

const profileMock = vi.mocked(fetchProfile);
const logoutMock = vi.mocked(logout);
let pinia: ReturnType<typeof createPinia>;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

function unknownSession() {
  const session = useSessionStore();
  session.setLogin({ token: "client:a" });
  return session;
}

async function memoryRouter(path = "/mine") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ["/mine", "/mine/tasks", "/home", "/login"].map((route) => ({
      path: route,
      component: { render: () => null },
    })),
  });
  await router.push(path);
  await router.isReady();
  return router;
}

describe("portal session helpers", () => {
  beforeEach(() => {
    writePortalToken("");
    pinia = createPinia();
    setActivePinia(pinia);
    profileMock.mockReset();
    logoutMock.mockReset();
  });

  afterEach(() => {
    disposePinia(pinia);
    writePortalToken("");
  });

  it("does not request a guest profile or an already known session", async () => {
    expect(await ensurePortalSession()).toBe(false);
    useSessionStore().setLogin({ token: "client:a", userId: 1 });
    expect(await ensurePortalSession()).toBe(true);
    expect(profileMock).not.toHaveBeenCalled();
  });

  it("loads a profile for the same unknown session", async () => {
    const session = unknownSession();
    profileMock.mockResolvedValueOnce(ok({ userId: 1, nickname: "账号 A", pointsBalance: 12 }));
    expect(await ensurePortalSession()).toBe(true);
    expect(session.userId).toBe(1);
    expect(session.nickname).toBe("账号 A");
    expect(session.pointsBalance).toBe(12);
  });

  it("clears the same session on a profile business failure", async () => {
    const session = unknownSession();
    profileMock.mockResolvedValueOnce(fail("auth.session.expired", "会话已过期"));
    expect(await ensurePortalSession()).toBe(false);
    expect(session.token).toBe("");
    expect(readPortalToken()).toBe("");
  });

  it("clears the same session on a profile exception", async () => {
    const session = unknownSession();
    profileMock.mockRejectedValueOnce(new Error("offline"));
    expect(await ensurePortalSession()).toBe(false);
    expect(session.authenticated).toBe(false);
  });

  it("does not restore a profile after logout while the request was pending", async () => {
    const session = unknownSession();
    const pending = deferred<Result<PortalProfileData>>();
    profileMock.mockReturnValueOnce(pending.promise);
    const ensuring = ensurePortalSession();
    session.clear();
    pending.resolve(ok({ userId: 1, nickname: "旧账号", pointsBalance: 99 }));
    expect(await ensuring).toBe(false);
    expect(session.userId).toBeNull();
    expect(session.nickname).toBe("");
    expect(session.pointsBalance).toBe(0);
  });

  it("keeps a newly known login when the former profile succeeds late", async () => {
    const session = unknownSession();
    const pending = deferred<Result<PortalProfileData>>();
    profileMock.mockReturnValueOnce(pending.promise);
    const ensuring = ensurePortalSession();
    session.setLogin({ token: "client:b", userId: 2, nickname: "账号 B" });
    session.setPointsBalance(12);
    pending.resolve(ok({ userId: 1, nickname: "旧账号", pointsBalance: 99 }));
    expect(await ensuring).toBe(true);
    expect(session.token).toBe("client:b");
    expect(session.userId).toBe(2);
    expect(session.nickname).toBe("账号 B");
    expect(session.pointsBalance).toBe(12);
  });

  it("does not consider an unknown new token verified using the old successful profile", async () => {
    const session = unknownSession();
    const pending = deferred<Result<PortalProfileData>>();
    profileMock.mockReturnValueOnce(pending.promise);
    const ensuring = ensurePortalSession();
    session.setLogin({ token: "client:b" });
    pending.resolve(ok({ userId: 1, nickname: "旧账号" }));
    expect(await ensuring).toBe(false);
    expect(session.token).toBe("client:b");
    expect(session.userId).toBeNull();
  });

  it("does not clear an unknown new token for a former profile failure", async () => {
    const session = unknownSession();
    const pending = deferred<Result<PortalProfileData>>();
    profileMock.mockReturnValueOnce(pending.promise);
    const ensuring = ensurePortalSession();
    session.setLogin({ token: "client:b" });
    pending.resolve(fail("auth.session.expired", "旧会话已过期"));
    expect(await ensuring).toBe(false);
    expect(session.token).toBe("client:b");
    expect(readPortalToken()).toBe("client:b");
  });

  it("does not clear a known new token for a former profile exception", async () => {
    const session = unknownSession();
    const pending = deferred<Result<PortalProfileData>>();
    profileMock.mockReturnValueOnce(pending.promise);
    const ensuring = ensurePortalSession();
    session.setLogin({ token: "client:b", userId: 2, nickname: "账号 B" });
    pending.reject(new Error("old request offline"));
    expect(await ensuring).toBe(true);
    expect(session.token).toBe("client:b");
    expect(session.userId).toBe(2);
    expect(session.nickname).toBe("账号 B");
  });

  it("does not restore a profile if another same-session request already cleared it", async () => {
    const session = unknownSession();
    const first = deferred<Result<PortalProfileData>>();
    const second = deferred<Result<PortalProfileData>>();
    profileMock.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const earlier = ensurePortalSession();
    const later = ensurePortalSession();
    second.resolve(fail("auth.session.expired", "会话已过期"));
    expect(await later).toBe(false);
    first.resolve(ok({ userId: 1, nickname: "旧账号" }));
    expect(await earlier).toBe(false);
    expect(session.token).toBe("");
    expect(session.nickname).toBe("");
  });

  it("keeps the new verified profile when the older account request finishes last", async () => {
    const session = unknownSession();
    const old = deferred<Result<PortalProfileData>>();
    const next = deferred<Result<PortalProfileData>>();
    profileMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise);
    const oldEnsure = ensurePortalSession();
    session.setLogin({ token: "client:b" });
    const newEnsure = ensurePortalSession();
    next.resolve(ok({ userId: 2, nickname: "账号 B", pointsBalance: 12 }));
    expect(await newEnsure).toBe(true);
    old.resolve(fail("auth.session.expired", "旧会话已过期"));
    expect(await oldEnsure).toBe(true);
    expect(session.userId).toBe(2);
    expect(session.pointsBalance).toBe(12);
  });

  it("leaves a new unknown profile pending when the old account request finishes first", async () => {
    const session = unknownSession();
    const old = deferred<Result<PortalProfileData>>();
    const next = deferred<Result<PortalProfileData>>();
    profileMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise);
    const oldEnsure = ensurePortalSession();
    session.setLogin({ token: "client:b" });
    const newEnsure = ensurePortalSession();
    old.resolve(ok({ userId: 1, nickname: "旧账号" }));
    expect(await oldEnsure).toBe(false);
    expect(session.userId).toBeNull();
    next.resolve(ok({ userId: 2, nickname: "账号 B" }));
    expect(await newEnsure).toBe(true);
    expect(session.userId).toBe(2);
  });

  it("resets the current session and redirects to login after logout", async () => {
    const session = unknownSession();
    const router = await memoryRouter();
    logoutMock.mockResolvedValueOnce(ok({ ok: true }));
    await logoutAndReset(router);
    expect(session.authenticated).toBe(false);
    expect(router.currentRoute.value.path).toBe("/login");
    expect(logoutMock).toHaveBeenCalledOnce();
  });

  it("still resets and redirects the same session when logout throws", async () => {
    const session = unknownSession();
    const router = await memoryRouter();
    logoutMock.mockRejectedValueOnce(new Error("offline"));
    await expect(logoutAndReset(router)).rejects.toThrow("offline");
    expect(session.authenticated).toBe(false);
    expect(router.currentRoute.value.path).toBe("/login");
  });

  it("does not reset or redirect a newer login when a former logout succeeds", async () => {
    const session = unknownSession();
    const router = await memoryRouter();
    const replace = vi.spyOn(router, "replace");
    const pending = deferred<Awaited<ReturnType<typeof logout>>>();
    logoutMock.mockReturnValueOnce(pending.promise);
    const leaving = logoutAndReset(router);
    session.setLogin({ token: "client:b", userId: 2 });
    pending.resolve(ok({ ok: true }));
    await leaving;
    expect(session.token).toBe("client:b");
    expect(replace).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/mine");
  });

  it("does not reset or redirect a newer login when a former logout throws", async () => {
    const session = unknownSession();
    const router = await memoryRouter();
    const replace = vi.spyOn(router, "replace");
    const pending = deferred<Awaited<ReturnType<typeof logout>>>();
    logoutMock.mockReturnValueOnce(pending.promise);
    const leaving = logoutAndReset(router);
    session.setLogin({ token: "client:b", userId: 2 });
    pending.reject(new Error("old request offline"));
    await expect(leaving).rejects.toThrow("old request offline");
    expect(session.token).toBe("client:b");
    expect(replace).not.toHaveBeenCalled();
  });

  it("does not repeat login navigation when the router is already there", async () => {
    unknownSession();
    const router = await memoryRouter("/login");
    const replace = vi.spyOn(router, "replace");
    logoutMock.mockResolvedValueOnce(ok({ ok: true }));
    await logoutAndReset(router);
    expect(replace).not.toHaveBeenCalled();
  });

  it("allows an explicit reset without a server request", () => {
    const session = unknownSession();
    resetPortalSession();
    expect(session.authenticated).toBe(false);
    expect(logoutMock).not.toHaveBeenCalled();
  });

  it.each([undefined, null, "", "https://example.com", "//example.com"])(
    "falls back to home for an unsafe or missing redirect %s", async (redirect) => {
      const router = await memoryRouter();
      await redirectAfterAuth(router, redirect);
      expect(router.currentRoute.value.path).toBe("/home");
    },
  );

  it("preserves an internal redirect with query and hash", async () => {
    const router = await memoryRouter();
    await redirectAfterAuth(router, "/mine/tasks?page=2#current");
    expect(router.currentRoute.value.fullPath).toBe("/mine/tasks?page=2#current");
  });
});
