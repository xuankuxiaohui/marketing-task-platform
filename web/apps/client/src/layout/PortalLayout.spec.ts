import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import PortalLayout from "./PortalLayout.vue";

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
