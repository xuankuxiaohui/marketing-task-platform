import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import { useSessionStore } from "./session";
import { usePrizePreviewStore } from "./prize-preview";

describe("private prize preview", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useSessionStore().clear();
  });

  it("removes the previous account's cached reward immediately on a token change", () => {
    const session = useSessionStore();
    session.setLogin({ token: "client:a", userId: 1 });
    const preview = usePrizePreviewStore();
    preview.set({ recordId: 1, prizeName: "账号A的奖品" });
    session.setLogin({ token: "client:b", userId: 2 });
    expect(preview.prize).toBeNull();
  });

  it("clears cached rewards on logout and preserves them during same-session profile updates", () => {
    const session = useSessionStore();
    session.setLogin({ token: "client:a", userId: 1 });
    const preview = usePrizePreviewStore();
    preview.set({ recordId: 1 });
    session.setProfile({ userId: 1, nickname: "新昵称" });
    expect(preview.prize?.recordId).toBe(1);
    session.clear();
    expect(preview.prize).toBeNull();
  });
});
