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

  it("renders home / mine tabs on the two roots", async () => {
    const wrapper = await mountLayout("/home");
    expect(wrapper.get('[data-testid="portal-tabbar"]').text()).toContain(zhCN.tab.home);
    expect(wrapper.get('[data-testid="portal-tabbar"]').text()).toContain(zhCN.tab.mine);
  });

  it("hides the tabbar on nested personal pages", async () => {
    const wrapper = await mountLayout("/mine/password");
    expect(wrapper.find('[data-testid="portal-tabbar"]').exists()).toBe(false);
  });
});
