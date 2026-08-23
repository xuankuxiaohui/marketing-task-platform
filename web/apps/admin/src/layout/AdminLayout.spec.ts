import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { DASHBOARD_ROUTE } from "@/router/dynamic";
import { usePermissionStore } from "@/store/permission";
import { useSessionStore } from "@/store/session";
import { useTagsStore } from "@/store/tags";

vi.mock("@/router/session", () => ({
  logoutAndReset: vi.fn(),
}));

import AdminLayout from "./AdminLayout.vue";

const wrappers: VueWrapper[] = [];

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
      { path: "/system/roles", component: { template: "<div />" } },
      { path: "/task/definitions", component: { template: "<div />" } },
      { path: "/task/instances", component: { template: "<div />" } },
      { path: "/metrics", component: { template: "<div />" } },
      { path: "/ad/positions", component: { template: "<div />" } },
      { path: "/ad/materials", component: { template: "<div />" } },
      { path: "/track/metadata", component: { template: "<div />" } },
      { path: "/track/events", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(AdminLayout, {
    attachTo: document.body,
    global: { plugins: [pinia, router] },
  });
  wrappers.push(wrapper);
  await flushPromises();
  return { wrapper, router, tags };
}

function seedGroupedMenus(): void {
  usePermissionStore().setMenus([
    { id: 2, name: "工作台", route: "/dashboard", component: "dashboard/index", sort: 1, icon: undefined, children: [] },
    { id: 3, name: "后台用户", route: "/system/users", component: "system/user/index", sort: 2, icon: undefined, children: [] },
    { id: 4, name: "角色权限", route: "/system/roles", component: "system/role/index", sort: 3, icon: undefined, children: [] },
    { id: 12, name: "任务列表", route: "/task/definitions", component: "task/definition/index", sort: 4, icon: undefined, children: [] },
    { id: 14, name: "实例管理", route: "/task/instances", component: "task/instance/index", sort: 5, icon: undefined, children: [] },
    {
      id: 13,
      name: "任务编辑（画布）",
      route: "/task/definitions/edit/:id?",
      component: "task/definition/edit",
      sort: 6,
      icon: undefined,
      children: [],
    },
    { id: 90, name: "运营看板", route: "/metrics", component: "metrics/index", sort: 7, icon: undefined, children: [] },
    { id: 27, name: "埋点元数据", route: "/track/metadata", component: "track/metadata/index", sort: 27, icon: undefined, children: [] },
    { id: 28, name: "事件调试", route: "/track/events", component: "track/event/index", sort: 28, icon: undefined, children: [] },
    { id: 53, name: "广告位", route: "/ad/positions", component: "ad/position/index", sort: 53, icon: undefined, children: [] },
    { id: 58, name: "广告素材", route: "/ad/materials", component: "ad/material/index", sort: 58, icon: undefined, children: [] },
  ]);
}

describe("AdminLayout tags", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    while (wrappers.length > 0) {
      wrappers.pop()?.unmount();
    }
  });

  it("exposes close others / left / right in zh-CN on the context menu", async () => {
    const { wrapper } = await mountLayout();
    await wrapper.get('[data-testid="admin-tag-/task/definitions"]').trigger("contextmenu", {
      clientX: 40,
      clientY: 12,
    });
    expect(wrapper.get('[data-testid="tag-close-others"]').text()).toBe(zhCN.layout.closeOthers);
    expect(wrapper.get('[data-testid="tag-close-left"]').text()).toBe(zhCN.layout.closeLeft);
    expect(wrapper.get('[data-testid="tag-close-right"]').text()).toBe(zhCN.layout.closeRight);
  });

  it("closes other tabs and keeps pinned dashboard", async () => {
    const { wrapper, tags, router } = await mountLayout();
    await wrapper.get('[data-testid="admin-tag-/task/definitions"]').trigger("contextmenu", {
      clientX: 40,
      clientY: 12,
    });
    await wrapper.get('[data-testid="tag-close-others"]').trigger("click");
    await flushPromises();
    expect(tags.items.map((item) => item.path)).toEqual([DASHBOARD_ROUTE, "/task/definitions"]);
    expect(router.currentRoute.value.path).toBe("/task/definitions");
  });

  it("closes tags on the left except pinned dashboard", async () => {
    const { wrapper, tags } = await mountLayout();
    await wrapper.get('[data-testid="admin-tag-/task/definitions"]').trigger("contextmenu", {
      clientX: 40,
      clientY: 12,
    });
    await wrapper.get('[data-testid="tag-close-left"]').trigger("click");
    expect(tags.items.map((item) => item.path)).toEqual([
      DASHBOARD_ROUTE,
      "/task/definitions",
      "/metrics",
    ]);
  });

  it("closes tags on the right and navigates away if the current tab is gone", async () => {
    const { wrapper, tags, router } = await mountLayout("/task/definitions");
    await wrapper.get('[data-testid="admin-tag-/system/users"]').trigger("contextmenu", {
      clientX: 40,
      clientY: 12,
    });
    await wrapper.get('[data-testid="tag-close-right"]').trigger("click");
    await flushPromises();
    expect(tags.items.map((item) => item.path)).toEqual([DASHBOARD_ROUTE, "/system/users"]);
    expect(router.currentRoute.value.path).toBe("/system/users");
  });

  it("closes other tabs from the tag context menu dropdown", async () => {
    const { wrapper, tags } = await mountLayout();
    await wrapper.get('[data-testid="admin-tag-/task/definitions"]').trigger("contextmenu", {
      clientX: 40,
      clientY: 12,
    });
    await flushPromises();
    expect(wrapper.find(".ant-dropdown").exists()).toBe(true);
    expect(wrapper.find(".admin-tag-menu").exists()).toBe(false);
    expect(wrapper.find('[data-testid="tag-actions"]').exists()).toBe(false);
    await wrapper.get('[data-testid="tag-close-others"]').trigger("click");
    expect(tags.items.map((item) => item.path)).toEqual([DASHBOARD_ROUTE, "/task/definitions"]);
  });

  it("hides native tag overflow and provides scroll buttons", async () => {
    const { wrapper } = await mountLayout();
    const tags = wrapper.get('[data-testid="admin-tags"]');
    expect(tags.classes()).toContain("admin-layout__tags");
    const tag = wrapper.get('[data-testid="admin-tag-/task/definitions"]');
    expect(tag.element.closest(".admin-tag-wrap")).not.toBeNull();
    const style = getComputedStyle(tag.element);
    expect(style.borderTopWidth).not.toBe("0px");
    expect(style.borderRightWidth).not.toBe("0px");
    expect(style.borderBottomWidth).not.toBe("0px");
    expect(style.borderLeftWidth).not.toBe("0px");
    const pad = Number.parseFloat(style.paddingLeft);
    expect(Number.isNaN(pad) ? 12 : pad).toBeGreaterThanOrEqual(8);
    expect(wrapper.find('[data-testid="tag-scroll-left"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="tag-scroll-right"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="tag-actions"]').exists()).toBe(false);
    const el = tags.element as HTMLElement;
    Object.defineProperty(el, "scrollWidth", { configurable: true, value: 900 });
    Object.defineProperty(el, "clientWidth", { configurable: true, value: 240 });
    window.dispatchEvent(new Event("resize"));
    await flushPromises();
    expect(wrapper.get('[data-testid="tag-scroll-left"]').classes()).toContain("admin-layout__tag-nav");
    expect(wrapper.get('[data-testid="tag-scroll-right"]').classes()).toContain("admin-layout__tag-nav");
    expect(wrapper.get('[data-testid="tag-scroll-left"]').attributes("aria-label")).toBe(zhCN.layout.scrollLeft);
    expect(wrapper.get('[data-testid="tag-scroll-right"]').attributes("aria-label")).toBe(zhCN.layout.scrollRight);
  });

  it("groups sidebar menus as collapsible submenus and opens only the active group", async () => {
    const { wrapper } = await mountLayout();
    seedGroupedMenus();
    await flushPromises();
    expect(wrapper.get(".admin-layout__brand").text()).toContain(zhCN.consoleSubtitle);
    expect(wrapper.text()).toContain(zhCN.menuGroup.dashboard);
    expect(wrapper.text()).toContain(zhCN.menuGroup.system);
    expect(wrapper.text()).toContain(zhCN.menuGroup.task);
    expect(wrapper.text()).toContain("任务列表");
    expect(wrapper.text()).not.toContain("任务编辑");
    expect(wrapper.find('[data-testid="sidebar-group-dashboard"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="sidebar-group-system"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="sidebar-group-task"]').exists()).toBe(true);
    const taskSubmenu = wrapper.get('[data-testid="sidebar-group-task"]').element.closest(".ant-menu-submenu");
    const systemSubmenu = wrapper.get('[data-testid="sidebar-group-system"]').element.closest(".ant-menu-submenu");
    expect(taskSubmenu?.classList.contains("ant-menu-submenu-open")).toBe(true);
    expect(systemSubmenu?.classList.contains("ant-menu-submenu-open")).toBe(false);
  });

  it("navigates from a leaf sidebar item and from a grouped child", async () => {
    const { wrapper, router } = await mountLayout();
    seedGroupedMenus();
    await flushPromises();
    await wrapper.get('[data-testid="sidebar-group-dashboard"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/dashboard");
    await wrapper.get('[data-testid="sidebar-group-system"]').trigger("click");
    await flushPromises();
    const systemSubmenu = wrapper.get('[data-testid="sidebar-group-system"]').element.closest(".ant-menu-submenu");
    expect(systemSubmenu?.classList.contains("ant-menu-submenu-open")).toBe(true);
    const userItem = wrapper.findAll(".ant-menu-item").find((item) => item.text().includes("后台用户"));
    expect(userItem?.find(".anticon").exists()).toBe(true);
    expect(userItem).toBeDefined();
    await userItem!.trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/system/users");
  });

  it("opens ad and track debug children", async () => {
    const { wrapper, router } = await mountLayout();
    seedGroupedMenus();
    await flushPromises();
    await wrapper.get('[data-testid="sidebar-group-ad"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="sidebar-group-ad"]').element.closest(".ant-menu-submenu")?.classList.contains("ant-menu-submenu-open")).toBe(true);
    await wrapper.get('[data-testid="sidebar-item-/ad/materials"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/ad/materials");
    await wrapper.get('[data-testid="sidebar-group-track"]').trigger("click");
    await flushPromises();
    await wrapper.get('[data-testid="sidebar-item-/track/events"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/track/events");
  });

  it("shows ruoyi-style breadcrumb and selected tag dot", async () => {
    const { wrapper, router } = await mountLayout();
    seedGroupedMenus();
    await router.push("/system/users");
    await flushPromises();
    expect(wrapper.get('[data-testid="admin-breadcrumb"]').text()).toContain(zhCN.layout.home);
    expect(wrapper.get('[data-testid="admin-breadcrumb"]').text()).toContain(zhCN.menuGroup.system);
    expect(wrapper.get('[data-testid="admin-breadcrumb"]').text()).toContain("后台用户");
    const active = wrapper.get('[data-testid="admin-tag-/system/users"]');
    expect(active.classes()).toContain("admin-tag--active");
    expect(active.find(".admin-tag__dot").exists()).toBe(true);
    expect(wrapper.get('[data-testid="admin-tag-/dashboard"]').text()).toContain(zhCN.layout.home);
  });

  it("collapses the sider to icon mode", async () => {
    const { wrapper } = await mountLayout();
    const sidebar = wrapper.get('[data-testid="admin-sidebar"]');
    expect(sidebar.classes()).toContain("ant-layout-sider-light");
    expect(wrapper.get('[data-testid="admin-content"]').classes()).toContain("admin-layout__content");
    expect(sidebar.classes()).not.toContain("ant-layout-sider-collapsed");
    await wrapper.get('[data-testid="sidebar-collapse"]').trigger("click");
    expect(sidebar.classes()).toContain("ant-layout-sider-collapsed");
    expect(wrapper.get('[data-testid="sidebar-collapse"]').attributes("aria-label")).toBe(zhCN.layout.expandMenu);
  });

  it("keeps sidebar and content as separate scroll panes and resets content on route change", async () => {
    const { wrapper, router } = await mountLayout();
    const sidebar = wrapper.get('[data-testid="admin-sidebar"]');
    const content = wrapper.get('[data-testid="admin-content"]');
    expect(sidebar.classes()).toContain("admin-layout__aside");
    expect(content.classes()).toContain("admin-layout__content");
    (content.element as HTMLElement).scrollTop = 120;
    await router.push("/system/users");
    await flushPromises();
    expect((content.element as HTMLElement).scrollTop).toBe(0);
  });
});
