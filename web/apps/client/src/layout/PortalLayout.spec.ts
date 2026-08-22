import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { ok } from "@/test-utils/result";

vi.mock("@/api/ad", () => ({
  fetchAdPosition: vi.fn(),
  dismissAdMaterial: vi.fn(),
}));

vi.mock("@/tracking", () => ({
  TRACK: {},
  track: vi.fn(),
}));

import { fetchAdPosition } from "@/api/ad";
import PortalLayout from "./PortalLayout.vue";

const adMock = vi.mocked(fetchAdPosition);

async function mountLayout(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: "/",
        component: PortalLayout,
        children: [
          { path: "home", component: { template: "<div />" }, meta: { tab: "home" } },
          { path: "mine", component: { template: "<div />" }, meta: { tab: "mine" } },
          { path: "mine/tasks", component: { template: "<div />" }, meta: { tab: "tasks" } },
          { path: "mine/prizes", component: { template: "<div />" }, meta: { tab: "prizes" } },
          { path: "mine/password", component: { template: "<div />" } },
        ],
      },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(PortalLayout, { global: { plugins: [router] } });
  return wrapper;
}

describe("PortalLayout", () => {
  beforeEach(() => {
    adMock.mockReset();
    adMock.mockResolvedValue(ok({ code: "x", form: "SPLASH", materials: [] }));
  });

  it("renders four club tabs on the hub roots", async () => {
    const wrapper = await mountLayout("/home");
    const bar = wrapper.get('[data-testid="portal-tabbar"]').text();
    expect(bar).toContain(zhCN.tab.home);
    expect(bar).toContain(zhCN.tab.tasks);
    expect(bar).toContain(zhCN.tab.prizes);
    expect(bar).toContain(zhCN.tab.mine);
  });

  it("keeps the tabbar on the task and prize roots", async () => {
    const tasks = await mountLayout("/mine/tasks");
    expect(tasks.get('[data-testid="tab-tasks"]').text()).toContain(zhCN.tab.tasks);
    const prizes = await mountLayout("/mine/prizes");
    expect(prizes.get('[data-testid="tab-prizes"]').text()).toContain(zhCN.tab.prizes);
  });

  it("hides the tabbar on nested personal pages", async () => {
    const wrapper = await mountLayout("/mine/password");
    expect(wrapper.find('[data-testid="portal-tabbar"]').exists()).toBe(false);
  });
});
