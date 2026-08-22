import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/ad", () => ({
  fetchAdPosition: vi.fn(),
  dismissAdMaterial: vi.fn(),
}));

vi.mock("@/api/activity", () => ({
  fetchActivities: vi.fn(),
  fetchActivityDetail: vi.fn(),
  postParticipate: vi.fn(),
}));

vi.mock("@/api/task", () => ({
  fetchTaskList: vi.fn(),
  startTask: vi.fn(),
  fetchMineTasks: vi.fn(),
  fetchTaskDetail: vi.fn(),
  clickTaskStep: vi.fn(),
  abandonTask: vi.fn(),
}));

vi.mock("@/tracking", () => ({
  TRACK: { TASK_START_CLICK: "task.start.click", AD_CAROUSEL_EXPOSURE: "ad.carousel.exposure", AD_CAROUSEL_CLICK: "ad.carousel.click" },
  track: vi.fn(),
  observeTaskCardExposure: vi.fn(() => () => undefined),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showFailToast: vi.fn(),
    showToast: vi.fn(),
  };
});

import { fetchActivities } from "@/api/activity";
import { fetchAdPosition } from "@/api/ad";
import { fetchTaskList } from "@/api/task";
import { showFailToast } from "vant";
import HomePage from "./index.vue";

const activityMock = vi.mocked(fetchActivities);
const adMock = vi.mocked(fetchAdPosition);
const listMock = vi.mocked(fetchTaskList);
const failToast = vi.mocked(showFailToast);

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/home", component: HomePage },
      { path: "/activity", component: { template: "<div />" } },
      { path: "/signin", component: { template: "<div />" } },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push("/home");
  await router.isReady();
  const wrapper = mount(HomePage, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("HomePage", () => {
  beforeEach(() => {
    activityMock.mockReset();
    listMock.mockReset();
    failToast.mockReset();
    adMock.mockReset();
    adMock.mockResolvedValue(ok({ code: "home_banner", form: "CAROUSEL", materials: [] }));
  });

  it("shows the generic empty copy when there are no activities", async () => {
    activityMock.mockResolvedValue(ok([]));
    const { wrapper } = await mountHome();
    expect(wrapper.get('[data-testid="home-empty"]').text()).toContain(zhCN.home.empty);
    expect(wrapper.get('[data-testid="home-signin-card"]').text()).toContain(zhCN.home.signin);
    expect(wrapper.find('[data-testid="home-list"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="task-card"]').exists()).toBe(false);
    expect(listMock).not.toHaveBeenCalled();
  });

  it("renders activity cards that open the activity page by id", async () => {
    activityMock.mockResolvedValue(
      ok([
        { id: 3, code: "summer", name: "夏季专题" },
        { id: 8, code: "autumn", name: "秋季专题" },
      ]),
    );
    const { wrapper, router } = await mountHome();
    expect(wrapper.get('[data-testid="home-activity-3"]').text()).toContain("夏季专题");
    expect(wrapper.get('[data-testid="home-activity-8"]').text()).toContain("秋季专题");
    await wrapper.get('[data-testid="home-activity-3"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/activity");
    expect(router.currentRoute.value.query.id).toBe("3");
    expect(listMock).not.toHaveBeenCalled();
  });

  it("routes the sign-in card to the existing calendar page", async () => {
    activityMock.mockResolvedValue(ok([]));
    const { wrapper, router } = await mountHome();
    await wrapper.get('[data-testid="home-signin-card"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/signin");
  });

  it("does not toast when the home banner slot is missing", async () => {
    activityMock.mockResolvedValue(ok([]));
    adMock.mockResolvedValue(fail("ad.position.not-found", "广告位不存在"));
    const { wrapper } = await mountHome();
    expect(wrapper.find('[data-testid="ad-carousel"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="home-signin-card"]').exists()).toBe(true);
    expect(failToast).not.toHaveBeenCalled();
  });
});
