import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/identity", () => ({
  pagePortalUsers: vi.fn(),
  fetchPortalUser: vi.fn(),
  updatePortalProfile: vi.fn(),
  disablePortalUser: vi.fn(),
  enablePortalUser: vi.fn(),
  resetPortalPassword: vi.fn(),
  deletePortalUser: vi.fn(),
}));

import { disablePortalUser, fetchPortalUser, pagePortalUsers } from "@/api/identity";
import PortalUserPage from "./index.vue";

const pageMock = vi.mocked(pagePortalUsers);
const detailMock = vi.mocked(fetchPortalUser);
const disableMock = vi.mocked(disablePortalUser);

describe("PortalUserPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    detailMock.mockReset();
    disableMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [{ id: 8, username: "u1", nickname: "用户", status: "ENABLED", province: "GD" }],
      }),
    );
    detailMock.mockResolvedValue(
      ok({
        id: 8,
        username: "u1",
        inProgressInstanceCount: 1,
        historyInstanceCount: 2,
        pointsBalance: 30,
        prizeSummary: { won: 1, granted: 0 },
        riskHits: 0,
        listStatus: [],
      }),
    );
    disableMock.mockResolvedValue(ok({ ok: true }));
  });

  it("loads detail and confirms disable", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(PortalUserPage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    await wrapper.get('[data-testid="portal-detail"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="portal-detail-card"]').text()).toContain("u1");
    await wrapper.get('[data-testid="portal-disable"]').trigger("click");
    expect(wrapper.get('[data-testid="confirm-message"]').text()).toBe(zhCN.confirm.disable);
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    await flushPromises();
    expect(disableMock).toHaveBeenCalledWith(8);
  });
});
