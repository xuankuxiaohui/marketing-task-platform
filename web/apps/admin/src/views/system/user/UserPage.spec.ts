import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/identity", () => ({
  pageUsers: vi.fn(),
  pageRoles: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  disableUser: vi.fn(),
  enableUser: vi.fn(),
  resetUserPassword: vi.fn(),
  deleteUser: vi.fn(),
}));

import { disableUser, pageRoles, pageUsers } from "@/api/identity";
import UserPage from "./index.vue";

const pageUsersMock = vi.mocked(pageUsers);
const pageRolesMock = vi.mocked(pageRoles);
const disableUserMock = vi.mocked(disableUser);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().$patch({
    userId: 99,
    permissions: Object.values(PERMS),
  });
  const wrapper = mount(UserPage, {
    global: { plugins: [pinia], directives: { auth } },
  });
  await flushPromises();
  return wrapper;
}

describe("AdminUserPage", () => {
  beforeEach(() => {
    pageUsersMock.mockReset();
    pageRolesMock.mockReset();
    disableUserMock.mockReset();
    pageRolesMock.mockResolvedValue(ok({ total: 0, records: [] }));
    pageUsersMock.mockResolvedValue(
      ok({
        total: 1,
        records: [{ id: 2, username: "ops", nickname: "运营", status: "ENABLED", roles: ["ops"] }],
      }),
    );
    disableUserMock.mockResolvedValue(ok({ ok: true }));
  });

  it("lists users and requires disable confirmation", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="user-table"]').text()).toContain("ops");
    await wrapper.get('[data-testid="user-disable"]').trigger("click");
    expect(wrapper.get('[data-testid="confirm-message"]').text()).toBe(zhCN.confirm.disable);
    await wrapper.get('[data-testid="confirm-cancel"]').trigger("click");
    expect(disableUserMock).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="user-disable"]').trigger("click");
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    await flushPromises();
    expect(disableUserMock).toHaveBeenCalledWith(2);
  });
});
