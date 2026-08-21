import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/identity", () => ({
  pageSessions: vi.fn(),
  kickSession: vi.fn(),
}));

import { kickSession, pageSessions } from "@/api/identity";
import SessionPage from "./index.vue";

const pageSessionsMock = vi.mocked(pageSessions);
const kickSessionMock = vi.mocked(kickSession);

describe("SessionManagePage", () => {
  beforeEach(() => {
    pageSessionsMock.mockReset();
    kickSessionMock.mockReset();
    pageSessionsMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            tokenLast4: "ab12",
            account: "alice",
            accountType: "admin",
            loginAt: "2026-08-20T00:00:00Z",
            lastActiveAt: "2026-08-20T01:00:00Z",
            ip: "127.0.0.1",
          },
        ],
      }),
    );
    kickSessionMock.mockResolvedValue(ok({ ok: true }));
  });

  it("prompts that kick logs out every session of the account", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(SessionPage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    await wrapper.get('[data-testid="session-kick"]').trigger("click");
    expect(wrapper.get('[data-testid="confirm-message"]').text()).toBe(zhCN.confirm.kick);
    expect(zhCN.confirm.kick).toBe("将下线该账号全部会话");
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    await flushPromises();
    expect(kickSessionMock).toHaveBeenCalledWith({
      accountType: "admin",
      account: "alice",
      tokenLast4: "ab12",
    });
  });
});
