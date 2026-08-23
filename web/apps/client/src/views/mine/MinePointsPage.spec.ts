import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/points", () => ({
  fetchPointsBalance: vi.fn(),
  fetchPointsTransactions: vi.fn(),
}));

vi.mock("@/tracking", () => ({
  TRACK: { POINTS_PAGE_VIEW: "points.page.view" },
  track: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showFailToast: vi.fn(),
  };
});

import { fetchPointsBalance, fetchPointsTransactions } from "@/api/points";
import { TRACK, track } from "@/tracking";
import MinePointsPage from "./MinePointsPage.vue";

const balanceMock = vi.mocked(fetchPointsBalance);
const txMock = vi.mocked(fetchPointsTransactions);
const trackMock = vi.mocked(track);

async function mountPoints(sessionBalance = 0) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/mine/points", component: MinePointsPage },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push("/mine/points");
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  useSessionStore().setPointsBalance(sessionBalance);
  const wrapper = mount(MinePointsPage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router, session: useSessionStore() };
}

describe("MinePointsPage", () => {
  beforeEach(() => {
    balanceMock.mockReset();
    txMock.mockReset();
    trackMock.mockReset();
  });

  it("shows empty ledger copy and refreshes balance on enter (R35.4)", async () => {
    balanceMock.mockResolvedValue(ok({ balance: 42 }));
    txMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const { wrapper, session } = await mountPoints(0);
    expect(trackMock).toHaveBeenCalledWith(TRACK.POINTS_PAGE_VIEW);
    expect(wrapper.get('[data-testid="points-balance"]').text()).toContain("42");
    expect(session.pointsBalance).toBe(42);
    expect(wrapper.get('[data-testid="mine-points-empty"]').text()).toContain(zhCN.empty.points);
  });

  it("renders signed amounts, running balance, and jumps to the source task", async () => {
    balanceMock.mockResolvedValue(ok({ balance: 30 }));
    txMock.mockResolvedValue(
      ok({
        total: 2,
        records: [
          {
            type: "EARN",
            amount: 10,
            balanceAfter: 30,
            sourceTaskId: 8,
            remark: "任务奖励",
            createdAt: "2026-08-20T04:00:00Z",
          },
          {
            type: "EXPIRE",
            amount: -5,
            balanceAfter: 20,
            createdAt: "2026-08-19T04:00:00Z",
          },
        ],
      }),
    );
    const { wrapper, router } = await mountPoints();
    const rows = wrapper.findAll('[data-testid="points-row"]');
    expect(rows[0]?.text()).toContain(zhCN.points.earn);
    expect(rows[0]?.text()).toContain("+10");
    expect(rows[0]?.text()).toContain("30");
    expect(rows[1]?.text()).toContain(zhCN.points.expire);
    expect(rows[1]?.text()).toContain("-5");
    await rows[0]?.trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/task/8");
  });
});
