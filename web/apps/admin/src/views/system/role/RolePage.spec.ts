import { message } from "ant-design-vue";
import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/identity", () => ({
  pageRoles: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  assignRolePermissions: vi.fn(),
  fetchPermissionTree: vi.fn(),
  fetchRolePermissions: vi.fn(),
  pageUsers: vi.fn(),
  updateUser: vi.fn(),
}));

import {
  assignRolePermissions,
  fetchPermissionTree,
  fetchRolePermissions,
  pageRoles,
  pageUsers,
  updateUser,
} from "@/api/identity";
import RolePage from "./index.vue";

const pageMock = vi.mocked(pageRoles);
const treeMock = vi.mocked(fetchPermissionTree);
const assignMock = vi.mocked(assignRolePermissions);
const permIdsMock = vi.mocked(fetchRolePermissions);
const usersMock = vi.mocked(pageUsers);
const updateUserMock = vi.mocked(updateUser);

function treeChecked(wrapper: ReturnType<typeof mount>, id: number): boolean {
  const title = wrapper.get(`[data-testid="perm-${id}"]`);
  const node = title.element.closest(".ant-tree-treenode");
  return Boolean(node?.querySelector(".ant-tree-checkbox-checked"));
}

async function checkTreeNode(wrapper: ReturnType<typeof mount>, id: number): Promise<void> {
  const title = wrapper.get(`[data-testid="perm-${id}"]`);
  const node = title.element.closest(".ant-tree-treenode");
  const box = node?.querySelector(".ant-tree-checkbox");
  expect(box).toBeTruthy();
  (box as HTMLElement).click();
  await flushPromises();
}

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(RolePage, {
    global: { plugins: [pinia], directives: { auth } },
    attachTo: document.body,
  });
  await flushPromises();
  return wrapper;
}

describe("RolePermissionPage", () => {
  let savedIds = [11];

  beforeEach(() => {
    savedIds = [11];
    pageMock.mockReset();
    treeMock.mockReset();
    assignMock.mockReset();
    permIdsMock.mockReset();
    usersMock.mockReset();
    updateUserMock.mockReset();
    pageMock.mockImplementation(async (params) => {
      const catalog = [
        { id: 1, code: "super-admin", name: "超级管理员", status: "ENABLED", userCount: 1 },
        { id: 2, code: "ops", name: "运营", status: "ENABLED", userCount: 3 },
        { id: 3, code: "auditor", name: "审计", status: "ENABLED", userCount: 1 },
      ];
      if (params?.all) {
        return ok({ total: catalog.length, records: catalog });
      }
      return ok({ total: 2, records: catalog.slice(0, 2) });
    });
    treeMock.mockResolvedValue(
      ok([{ id: 10, name: "后台用户", type: "MENU", children: [{ id: 11, name: "查询", code: "identity:admin-user:query" }] }]),
    );
    permIdsMock.mockImplementation(async () => ok({ permissionIds: [...savedIds] }));
    assignMock.mockImplementation(async (_id, body) => {
      savedIds = [...(body.permissionIds ?? [])];
      return ok({ ok: true });
    });
    usersMock.mockResolvedValue(
      ok({
        total: 1,
        records: [{ id: 8, username: "ops_a", nickname: "运营甲", status: "ENABLED", roles: ["ops", "auditor"] }],
      }),
    );
    updateUserMock.mockResolvedValue(ok({ ok: true }));
  });

  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("hides assign for super-admin and overwrites permissions on submit", async () => {
    const wrapper = await mountPage();
    const assignButtons = wrapper.findAll('[data-testid="role-assign"]');
    expect(assignButtons).toHaveLength(1);
    await assignButtons[0].trigger("click");
    await flushPromises();
    expect(wrapper.find(".ant-tree").exists()).toBe(true);
    expect(wrapper.find(".ant-tree-switcher").exists()).toBe(true);
    expect(treeChecked(wrapper, 11)).toBe(true);
    await wrapper.get('[data-testid="form-submit"]').trigger("click");
    await flushPromises();
    expect(assignMock).toHaveBeenCalledWith(2, { permissionIds: [11] });
    expect(document.body.textContent).toContain(zhCN.common.saved);
  });

  it("checks child permissions when a parent menu is checked", async () => {
    savedIds = [];
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="role-assign"]').trigger("click");
    await flushPromises();
    await checkTreeNode(wrapper, 10);
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(assignMock).toHaveBeenCalledWith(2, { permissionIds: expect.arrayContaining([10, 11]) });
  });

  it("reopens with the persisted permission set", async () => {
    savedIds = [];
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="role-assign"]').trigger("click");
    await flushPromises();
    expect(treeChecked(wrapper, 11)).toBe(false);
    await checkTreeNode(wrapper, 10);
    await wrapper.get('[data-testid="form-submit"]').trigger("click");
    await flushPromises();
    expect(assignMock).toHaveBeenCalledWith(2, { permissionIds: expect.arrayContaining([10, 11]) });
    await wrapper.get('[data-testid="role-assign"]').trigger("click");
    await flushPromises();
    expect(permIdsMock).toHaveBeenLastCalledWith(2);
    expect(treeChecked(wrapper, 10)).toBe(true);
    expect(treeChecked(wrapper, 11)).toBe(true);
  });

  it("keeps the assign dialog open and shows the fail message plus traceId", async () => {
    assignMock.mockResolvedValue(fail("auth.role.built-in", "内置角色不可改", "trace-role"));
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="role-assign"]').trigger("click");
    await flushPromises();
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(wrapper.find('[data-testid="form-dialog"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="form-dialog"]').text()).toContain("内置角色不可改");
    expect(wrapper.get('[data-testid="form-dialog"]').text()).toContain("trace-role");
    expect(wrapper.get('[data-testid="form-dialog"]').find('[data-testid="copy-trace"]').exists()).toBe(true);
  });

  it("lists role users and revokes this role without dropping others", async () => {
    const successSpy = vi.spyOn(message, "success");
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="role-assign"]').trigger("click");
    await flushPromises();
    expect(usersMock).toHaveBeenCalledWith({ roleId: 2, page: 1, pageSize: 100 });
    expect(wrapper.get('[data-testid="role-holder-8"]').text()).toContain("ops_a");
    await wrapper.get('[data-testid="role-revoke"]').trigger("click");
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    await flushPromises();
    expect(updateUserMock).toHaveBeenCalledWith(8, { nickname: "运营甲", roleIds: [3] });
    expect(wrapper.get('[data-testid="role-holders"]').text()).toContain(zhCN.role.noHolders);
    expect(successSpy).toHaveBeenCalledWith(zhCN.role.revoked);
    successSpy.mockRestore();
  });
});
