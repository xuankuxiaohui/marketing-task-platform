import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import type { PrizeCardView } from "@/api/prize";
import { usePrizePreviewStore } from "@/store/prize-preview";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/prize", () => ({
  fetchPrizeList: vi.fn(),
  claimPrize: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return { ...actual, showToast: vi.fn(), showFailToast: vi.fn() };
});

import { claimPrize } from "@/api/prize";
import PrizeDetailPage from "./PrizeDetailPage.vue";

const claimMock = vi.mocked(claimPrize);

function prize(overrides: Partial<PrizeCardView> = {}): PrizeCardView {
  return {
    recordId: 11,
    prizeName: "积分礼包",
    prizeImage: "https://cdn.example/p.png",
    status: "WON",
    fulfillmentStatus: "NONE",
    obtainedAt: "2026-08-19T04:00:00.000Z",
    sourceTaskId: 22,
    sourceTaskName: "每日浏览",
    ...overrides,
  };
}

async function mountDetail() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/mine/prizes/:recordId", component: PrizeDetailPage },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push("/mine/prizes/11");
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  usePrizePreviewStore().set(prize());
  const wrapper = mount(PrizeDetailPage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("PrizeDetailPage", () => {
  beforeEach(() => {
    claimMock.mockReset();
  });

  it("renders cached prize fields and opens the source task", async () => {
    const { wrapper, router } = await mountDetail();
    expect(wrapper.get('[data-testid="prize-detail-name"]').text()).toBe("积分礼包");
    expect(wrapper.get('[data-testid="prize-obtained-at"]').text()).toContain(zhCN.prize.obtainedAt);
    await wrapper.get('[data-testid="prize-source"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/task/22");
  });
});
