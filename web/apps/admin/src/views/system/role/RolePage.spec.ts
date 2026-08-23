import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
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

  async function checkTreeNode(wrapper: ReturnType<typeof mount>, id: number): Promise<void> {
    const title = wrapper.get(`[data-testid="perm-${id}"]`);
    const node = title.element.closest(".ant-tree-treenode");
    const box = node?.querySelector(".ant-tree-checkbox");
    expect(box).toBeTruthy();
    (box as HTMLElement).click();
    await flushPromises();
  }

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
    expect(wrapper.find(".ant-tree").exists()).toBe(true);
    expect(wrapper.find(".ant-tree-switcher").exists()).toBe(true);
    await checkTreeNode(wrapper, 11);
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(assignMock).toHaveBeenCalledWith(2, { permissionIds: expect.arrayContaining([10, 11]) });
  });

  it("checks child permissions when a parent menu is checked", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(RolePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    await wrapper.get('[data-testid="role-assign"]').trigger("click");
    await flushPromises();
    await checkTreeNode(wrapper, 10);
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(assignMock).toHaveBeenCalledWith(2, { permissionIds: expect.arrayContaining([10, 11]) });
  });
});
