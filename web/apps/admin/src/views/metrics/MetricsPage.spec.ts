import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { setControl, visibleText } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("echarts", () => ({
  init: () => ({ setOption: vi.fn(), dispose: vi.fn(), resize: vi.fn() }),
}));

vi.mock("@/api/metrics", () => ({
  fetchFunnel: vi.fn(),
  fetchSpendMetrics: vi.fn(),
  fetchRiskMetrics: vi.fn(),
  fetchAdMetrics: vi.fn(),
}));

import { fetchAdMetrics, fetchFunnel, fetchRiskMetrics, fetchSpendMetrics } from "@/api/metrics";
import MetricsDashboardPage from "./index.vue";

const funnelMock = vi.mocked(fetchFunnel);
const spendMock = vi.mocked(fetchSpendMetrics);
const riskMock = vi.mocked(fetchRiskMetrics);
const adMock = vi.mocked(fetchAdMetrics);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const wrapper = mount(MetricsDashboardPage, { global: { plugins: [pinia] } });
  await flushPromises();
  return wrapper;
}

describe("MetricsDashboardPage", () => {
  beforeEach(() => {
    funnelMock.mockReset();
    spendMock.mockReset();
    riskMock.mockReset();
    adMock.mockReset();
    funnelMock.mockResolvedValue(
      ok({
        records: [
          {
            period: "2026-08-20",
            dimKey: "1",
            exposureCount: 10,
            startCount: 4,
            completeCount: 2,
            startRate: 0.4,
            completeRate: 0.5,
          },
          {
            period: "2026-08-20",
            dimKey: "2",
            exposureCount: 0,
            startCount: 1,
            completeCount: 0,
            startRate: null,
            completeRate: 0,
          },
        ],
      }),
    );
    spendMock.mockResolvedValue(
      ok({
        records: [
          {
            period: "2026-08-20",
            dimKey: "POINTS",
            arrivedCount: 2,
            arrivedCostFen: 88,
            sendingCount: 1,
            sendingCostFen: 7,
            remainingStock: 8,
            totalStock: 20,
          },
        ],
      }),
    );
    riskMock.mockResolvedValue(
      ok({
        records: [
          { period: "2026-08-20", dimKey: "R-a", hitCount: 10, interceptCount: 4, interceptRate: 0.4 },
        ],
      }),
    );
    adMock.mockResolvedValue(
      ok({
        records: [
          { period: "2026-08-20", dimKey: "home:m1", exposureCount: 8, clickCount: 2, ctr: 0.25 },
        ],
      }),
    );
  });

  it("renders funnel spend risk and ad tables including dash rate", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="funnel-table"]').text()).toContain("10");
    expect(wrapper.get('[data-testid="funnel-table"]').text()).toContain("—");
    expect(wrapper.get('[data-testid="spend-table"]').text()).toContain("POINTS");
    expect(wrapper.get('[data-testid="risk-table"]').text()).toContain("R-a");
    expect(wrapper.get('[data-testid="ad-table"]').text()).toContain("home:m1");
    expect(visibleText(wrapper, "metrics-query")).toContain(zhCN.common.query);
  });

  it("queries with grain and dimKey", async () => {
    const wrapper = await mountPage();
    await setControl(wrapper, "filter-grain", "WEEK");
    await setControl(wrapper, "filter-dim", "1");
    await wrapper.get('[data-testid="metrics-query"]').trigger("click");
    await flushPromises();
    expect(funnelMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ grain: "WEEK", dimKey: "1" }),
    );
  });
});
