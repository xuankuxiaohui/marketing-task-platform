import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { setControl } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("@/api/identity", () => ({
  pageRoles: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  assignRolePermissions: vi.fn(),
  fetchPermissionTree: vi.fn(),
}));

import { assignRolePermissions, fetchPermissionTree, pageRoles } from "@/api/identity";
import RolePage from "./index.vue";

const pageMock = vi.mocked(pageRoles);
const treeMock = vi.mocked(fetchPermissionTree);
const assignMock = vi.mocked(assignRolePermissions);

describe("RolePermissionPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    treeMock.mockReset();
    assignMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 2,
        records: [
          { id: 1, code: "super-admin", name: "超级管理员", status: "ENABLED", userCount: 1 },
          { id: 2, code: "ops", name: "运营", status: "ENABLED", userCount: 3 },
        ],
      }),
    );
    treeMock.mockResolvedValue(
      ok([{ id: 10, name: "后台用户", type: "MENU", children: [{ id: 11, name: "查询", code: "identity:admin-user:query" }] }]),
    );
    assignMock.mockResolvedValue(ok({ ok: true }));
  });

  it("hides assign for super-admin and overwrites permissions on submit", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(RolePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    const assignButtons = wrapper.findAll('[data-testid="role-assign"]');
    expect(assignButtons).toHaveLength(1);
    await assignButtons[0].trigger("click");
    await flushPromises();
    await setControl(wrapper, "perm-11", true);
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(assignMock).toHaveBeenCalledWith(2, { permissionIds: [11] });
  });
});
