import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/activity", () => ({
  fetchActivities: vi.fn(),
  fetchActivityDetail: vi.fn(),
  postParticipate: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return { ...actual, showToast: vi.fn(), showFailToast: vi.fn() };
});

import { fetchActivities } from "@/api/activity";
import ActivityHubPage from "./ActivityHubPage.vue";

const listMock = vi.mocked(fetchActivities);

async function mountHub() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/activities", component: ActivityHubPage },
      { path: "/activity", component: { template: "<div />" } },
    ],
  });
  await router.push("/activities");
  await router.isReady();
  const wrapper = mount(ActivityHubPage, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("ActivityHubPage", () => {
  beforeEach(() => {
    listMock.mockReset();
  });

  it("renders in-progress activities from the catalog and opens detail", async () => {
    listMock.mockResolvedValue(
      ok([
        {
          id: 3,
          code: "summer",
          name: "夏季专题",
          startTime: "2026-08-01T00:00:00Z",
          endTime: "2026-08-31T16:00:00Z",
        },
      ]),
    );
    const { wrapper, router } = await mountHub();
    expect(wrapper.get('[data-testid="activity-hub-card"]').text()).toContain("夏季专题");
    await wrapper.get('[data-testid="activity-hub-card"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/activity");
    expect(router.currentRoute.value.query.id).toBe("3");
  });

  it("shows empty copy only when the activity catalog is empty", async () => {
    listMock.mockResolvedValue(ok([]));
    const { wrapper } = await mountHub();
    expect(wrapper.get('[data-testid="activity-hub-empty"]').text()).toContain(zhCN.activity.empty);
  });

  it("shows empty copy when the catalog request fails", async () => {
    listMock.mockResolvedValue(fail("activity.not-found", "不存在"));
    const { wrapper } = await mountHub();
    expect(wrapper.get('[data-testid="activity-hub-empty"]').text()).toContain(zhCN.activity.empty);
  });
});
