import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { ok } from "@/test-utils/result";

vi.mock("@/api/activity", () => ({
  pageParticipations: vi.fn(),
  activityStats: vi.fn(),
}));

import { activityStats, pageParticipations } from "@/api/activity";
import ActivityParticipationPage from "./index.vue";

const pageMock = vi.mocked(pageParticipations);
const statsMock = vi.mocked(activityStats);

describe("ActivityParticipationPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    statsMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 9,
            activityId: 3,
            userId: 12,
            periodKey: "2026-08-20",
            result: "PASS",
            createdAt: "2026-08-20T04:00:00Z",
          },
        ],
      }),
    );
  });

  it("lists participation records", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const wrapper = mount(ActivityParticipationPage, { global: { plugins: [pinia] } });
    await flushPromises();
    expect(wrapper.get('[data-testid="activity-participation-table"]').text()).toContain("PASS");
    expect(wrapper.get('[data-testid="activity-participation-query"]').text()).toContain(zhCN.common.query);
  });
});
