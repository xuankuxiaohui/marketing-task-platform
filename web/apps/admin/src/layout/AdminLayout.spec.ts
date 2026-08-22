import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { DASHBOARD_ROUTE } from "@/router/dynamic";
import { usePermissionStore } from "@/store/permission";
import { useSessionStore } from "@/store/session";
import { useTagsStore } from "@/store/tags";

vi.mock("@/router/session", () => ({
  logoutAndReset: vi.fn(),
}));

import AdminLayout from "./AdminLayout.vue";

async function mountLayout(path = "/task/definitions") {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().$patch({ userId: 1, nickname: "超管", username: "admin" });
  const tags = useTagsStore();
  tags.push({ path: DASHBOARD_ROUTE, title: "工作台" });
  tags.push({ path: "/system/users", title: "后台用户" });
  tags.push({ path: "/task/definitions", title: "任务列表" });
  tags.push({ path: "/metrics", title: "运营看板" });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: DASHBOARD_ROUTE, component: { template: "<div />" } },
      { path: "/system/users", component: { template: "<div />" } },
      { path: "/task/definitions", component: { template: "<div />" } },
      { path: "/metrics", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(AdminLayout, {
    global: { plugins: [pinia, router] },
  });
  await flushPromises();
  return { wrapper, router, tags };
}

describe("AdminLayout tags", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("exposes close others / left / right in zh-CN on the context menu", async () => {
    const { wrapper } = await mountLayout();
    await wrapper.get('[data-testid="admin-tag-/task/definitions"]').trigger("contextmenu");
    expect(wrapper.get('[data-testid="tag-close-others"]').text()).toBe(zhCN.layout.closeOthers);
    expect(wrapper.get('[data-testid="tag-close-left"]').text()).toBe(zhCN.layout.closeLeft);
    expect(wrapper.get('[data-testid="tag-close-right"]').text()).toBe(zhCN.layout.closeRight);
  });

  it("closes other tabs and keeps pinned dashboard", async () => {
    const { wrapper, tags, router } = await mountLayout();
    await wrapper.get('[data-testid="admin-tag-/task/definitions"]').trigger("contextmenu");
    await wrapper.get('[data-testid="tag-close-others"]').trigger("click");
    await flushPromises();
    expect(tags.items.map((item) => item.path)).toEqual([DASHBOARD_ROUTE, "/task/definitions"]);
    expect(router.currentRoute.value.path).toBe("/task/definitions");
  });

  it("closes tags on the left except pinned dashboard", async () => {
    const { wrapper, tags } = await mountLayout();
    await wrapper.get('[data-testid="admin-tag-/task/definitions"]').trigger("contextmenu");
    await wrapper.get('[data-testid="tag-close-left"]').trigger("click");
    expect(tags.items.map((item) => item.path)).toEqual([
      DASHBOARD_ROUTE,
      "/task/definitions",
      "/metrics",
    ]);
  });

  it("closes tags on the right and navigates away if the current tab is gone", async () => {
    const { wrapper, tags, router } = await mountLayout("/task/definitions");
    await wrapper.get('[data-testid="admin-tag-/system/users"]').trigger("contextmenu");
    await wrapper.get('[data-testid="tag-close-right"]').trigger("click");
    await flushPromises();
    expect(tags.items.map((item) => item.path)).toEqual([DASHBOARD_ROUTE, "/system/users"]);
    expect(router.currentRoute.value.path).toBe("/system/users");
  });

  it("groups sidebar menus with zh-CN titles and one-click items", async () => {
    const { wrapper } = await mountLayout();
    usePermissionStore().setMenus([
      { id: 2, name: "工作台", route: "/dashboard", component: "dashboard/index", sort: 1, icon: undefined, children: [] },
      { id: 3, name: "后台用户", route: "/system/users", component: "system/user/index", sort: 2, icon: undefined, children: [] },
      {
        id: 13,
        name: "任务编辑（画布）",
        route: "/task/definitions/edit/:id?",
        component: "task/definition/edit",
        sort: 3,
        icon: undefined,
        children: [],
      },
      { id: 90, name: "运营看板", route: "/metrics", component: "metrics/index", sort: 4, icon: undefined, children: [] },
    ]);
    await flushPromises();
    expect(wrapper.text()).toContain(zhCN.menuGroup.dashboard);
    expect(wrapper.text()).toContain(zhCN.menuGroup.system);
    expect(wrapper.text()).toContain(zhCN.menuGroup.other);
    expect(wrapper.text()).toContain("后台用户");
    expect(wrapper.text()).not.toContain("任务编辑");
    expect(wrapper.find(".el-sub-menu").exists()).toBe(false);
    expect(wrapper.findAll(".el-menu-item-group").length).toBe(3);
  });
});
