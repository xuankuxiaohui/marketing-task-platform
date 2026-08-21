import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import EmptyListPage from "./EmptyListPage.vue";

async function mountEmpty(path: string, emptyKind: "tasks" | "prizes" | "points") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path, component: EmptyListPage, meta: { title: "列表", emptyKind } },
      { path: "/home", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(EmptyListPage, { global: { plugins: [router] } });
  return { wrapper, router };
}

describe("EmptyListPage", () => {
  it("shows task empty copy and a guide to the home list", async () => {
    const { wrapper } = await mountEmpty("/mine/tasks", "tasks");
    expect(wrapper.get('[data-testid="empty-list"]').text()).toContain(zhCN.empty.tasks);
    expect(wrapper.get('[data-testid="empty-go-home"]').text()).toBe(zhCN.empty.goHome);
  });

  it("shows prize and points empty copy", async () => {
    const prizes = await mountEmpty("/mine/prizes", "prizes");
    expect(prizes.wrapper.get('[data-testid="empty-list"]').text()).toContain(zhCN.empty.prizes);
    const points = await mountEmpty("/mine/points", "points");
    expect(points.wrapper.get('[data-testid="empty-list"]').text()).toContain(zhCN.empty.points);
  });
});
