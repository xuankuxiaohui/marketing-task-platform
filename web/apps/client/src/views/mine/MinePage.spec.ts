import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/task", () => ({
  fetchMineTasks: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showConfirmDialog: vi.fn().mockResolvedValue(undefined),
    showFailToast: vi.fn(),
  };
});

import { fetchMineTasks } from "@/api/task";
import MinePage from "./index.vue";

const mineMock = vi.mocked(fetchMineTasks);

async function mountMine() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/mine", component: MinePage },
      { path: "/mine/tasks", component: { template: "<div />" } },
      { path: "/mine/profile", component: { template: "<div />" } },
      { path: "/signin", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/mine");
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().setLogin({ token: "client:t", userId: 9, nickname: "用户9" });
  useSessionStore().setProfile({
    userId: 9,
    username: "bob_01",
    nickname: "用户9",
    pointsBalance: 12,
    tags: [],
  });
  const wrapper = mount(MinePage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("MinePage", () => {
  beforeEach(() => {
    mineMock.mockReset();
    mineMock.mockResolvedValue(ok({ total: 3, records: [] }));
  });

  it("renders profile card, entries and in-progress badge", async () => {
    const { wrapper } = await mountMine();
    expect(wrapper.get('[data-testid="profile-nickname"]').text()).toBe("用户9");
    expect(wrapper.get('[data-testid="profile-points"]').text()).toContain("12");
    expect(wrapper.get('[data-testid="entry-tasks"]').text()).toContain(zhCN.mine.tasks);
    expect(wrapper.get('[data-testid="entry-prizes"]').text()).toContain(zhCN.mine.prizes);
    expect(wrapper.get('[data-testid="entry-points"]').text()).toContain(zhCN.mine.pointsDetail);
    expect(wrapper.get('[data-testid="entry-signin"]').text()).toContain(zhCN.mine.signin);
    expect(wrapper.get('[data-testid="entry-password"]').text()).toContain(zhCN.mine.password);
    expect(wrapper.get('[data-testid="entry-logout"]').text()).toContain(zhCN.mine.logout);
  });
});
