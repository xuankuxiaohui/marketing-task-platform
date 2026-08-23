import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/ad", () => ({
  fetchAdPosition: vi.fn(),
  dismissAdMaterial: vi.fn(),
}));

vi.mock("@/tracking", () => ({
  TRACK: { AD_CAROUSEL_CLICK: "ad.carousel.click" },
  track: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return { ...actual, showToast: vi.fn() };
});

import { fetchAdPosition } from "@/api/ad";
import ActivityHubPage from "./ActivityHubPage.vue";

const adMock = vi.mocked(fetchAdPosition);

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
    adMock.mockReset();
  });

  it("renders ad banners that jump to the activity route", async () => {
    adMock.mockResolvedValue(
      ok({
        code: "home_banner",
        form: "CAROUSEL",
        materials: [
          {
            materialId: 2,
            trackId: "t2",
            title: "夏季专题",
            imageUrl: "https://cdn.example/b.png",
            jumpType: "ROUTE",
            jumpParams: { route: "activity", activityId: 3 },
            weight: 1,
          },
        ],
      }),
    );
    const { wrapper, router } = await mountHub();
    expect(wrapper.get('[data-testid="activity-hub-banner"]').text()).toContain("夏季专题");
    await wrapper.get('[data-testid="activity-hub-banner"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/activity");
    expect(router.currentRoute.value.query.id).toBe("3");
  });

  it("shows empty copy when the banner slot is missing", async () => {
    adMock.mockResolvedValue(fail("ad.position.not-found", "广告位不存在"));
    const { wrapper } = await mountHub();
    expect(wrapper.get('[data-testid="activity-hub-empty"]').text()).toContain(zhCN.activity.empty);
  });
});
