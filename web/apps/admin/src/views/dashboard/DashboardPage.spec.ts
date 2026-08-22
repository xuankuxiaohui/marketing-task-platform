import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/metrics", () => ({
  fetchFunnel: vi.fn(),
  fetchSpendMetrics: vi.fn(),
  fetchRiskMetrics: vi.fn(),
  fetchAdMetrics: vi.fn(),
}));

import { fetchAdMetrics, fetchFunnel, fetchRiskMetrics, fetchSpendMetrics } from "@/api/metrics";
import DashboardPage from "./index.vue";

const funnelMock = vi.mocked(fetchFunnel);
const spendMock = vi.mocked(fetchSpendMetrics);
const riskMock = vi.mocked(fetchRiskMetrics);
const adMock = vi.mocked(fetchAdMetrics);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().nickname = "运营";
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/metrics", component: { template: "<div />" } },
    ],
  });
  const wrapper = mount(DashboardPage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return wrapper;
}

describe("DashboardPage", () => {
  beforeEach(() => {
    funnelMock.mockReset();
    spendMock.mockReset();
    riskMock.mockReset();
    adMock.mockReset();
    funnelMock.mockResolvedValue(ok({ records: [{ period: "d", dimKey: "1", exposureCount: 5, startCount: 1, completeCount: 0 }] }));
    spendMock.mockResolvedValue(
      ok({
        records: [
          {
            period: "d",
            dimKey: "POINTS",
            arrivedCount: 1,
            arrivedCostFen: 20,
            sendingCount: 0,
            sendingCostFen: 0,
            remainingStock: 1,
            totalStock: 2,
          },
        ],
      }),
    );
    riskMock.mockResolvedValue(ok({ records: [{ period: "d", dimKey: "R-a", hitCount: 3, interceptCount: 2 }] }));
    adMock.mockResolvedValue(ok({ records: [{ period: "d", dimKey: "home:m1", exposureCount: 4, clickCount: 1 }] }));
  });

  it("shows aggregated cards and link to metrics page", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="card-funnel"]').text()).toContain("5");
    expect(wrapper.get('[data-testid="card-spend"]').text()).toContain("0.20");
    expect(wrapper.get('[data-testid="card-spend"]').text()).toContain("20");
    expect(wrapper.get('[data-testid="card-risk"]').text()).toContain("2");
    expect(wrapper.get('[data-testid="card-ad"]').text()).toContain("1");
    expect(wrapper.get('[data-testid="dashboard-inbox"]').text()).toContain(zhCN.dashboard.interceptToday);
    expect(wrapper.get('[data-testid="metrics-link"]').text()).toContain(zhCN.metrics.open);
    expect(wrapper.get('[data-testid="metrics-link"]').attributes("href")).toBe("/metrics");
  });
});
