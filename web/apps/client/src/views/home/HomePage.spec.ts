import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { PORTAL_PRIMARY } from "@/theme";
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

vi.mock("@/api/points", () => ({
  fetchPointsBalance: vi.fn(),
  fetchPointsTransactions: vi.fn(),
}));

vi.mock("@/api/task", () => ({
  fetchTaskList: vi.fn(),
  startTask: vi.fn(),
  fetchMineTasks: vi.fn(),
  fetchTaskDetail: vi.fn(),
  clickTaskStep: vi.fn(),
  abandonTask: vi.fn(),
}));

vi.mock("@/api/signin", () => ({
  fetchSigninActivities: vi.fn(),
  fetchSigninCalendar: vi.fn(),
  postCheckin: vi.fn(),
  postCatchup: vi.fn(),
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
import { fetchPointsBalance } from "@/api/points";
import { fetchSigninActivities, fetchSigninCalendar } from "@/api/signin";
import { fetchTaskList } from "@/api/task";
import { showFailToast } from "vant";
import HomePage from "./index.vue";

const activityMock = vi.mocked(fetchActivities);
const adMock = vi.mocked(fetchAdPosition);
const pointsMock = vi.mocked(fetchPointsBalance);
const listMock = vi.mocked(fetchTaskList);
const signinListMock = vi.mocked(fetchSigninActivities);
const signinCalendarMock = vi.mocked(fetchSigninCalendar);
const failToast = vi.mocked(showFailToast);

async function mountHome(loggedIn = false) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/home", component: HomePage },
      { path: "/activity", component: { template: "<div />" } },
      { path: "/signin", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
      { path: "/mine/points", component: { template: "<div />" } },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push("/home");
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.clear();
  if (loggedIn) {
    session.setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  }
  const wrapper = mount(HomePage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router, overlay: useLoginOverlayStore() };
}

describe("HomePage", () => {
  beforeEach(() => {
    activityMock.mockReset();
    listMock.mockReset();
    failToast.mockReset();
    pointsMock.mockReset();
    signinListMock.mockReset();
    signinCalendarMock.mockReset();
    adMock.mockReset();
    adMock.mockResolvedValue(ok({ code: "home_banner", form: "CAROUSEL", materials: [] }));
    pointsMock.mockResolvedValue(ok({ balance: 12 }));
    listMock.mockResolvedValue(ok({ total: 0, records: [] }));
    signinListMock.mockResolvedValue(ok([]));
  });

  it("keeps the guest hub on /home without routing to /login", async () => {
    const { wrapper, router, overlay } = await mountHome(false);
    expect(router.currentRoute.value.path).toBe("/home");
    expect(overlay.visible).toBe(false);
    expect(wrapper.get('[data-testid="home-signin-card"]').text()).toContain(zhCN.home.signin);
    expect(wrapper.find('[data-testid="home-signin-calendar"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="home-activity-list"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="home-empty"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain(zhCN.home.activities);
    expect(pointsMock).not.toHaveBeenCalled();
    expect(signinListMock).not.toHaveBeenCalled();
    expect(activityMock).not.toHaveBeenCalled();
    expect(listMock).not.toHaveBeenCalled();
  });

  it("opens overlay login from the compact sign-in action without leaving home", async () => {
    const { wrapper, router, overlay } = await mountHome(false);
    await wrapper.get('[data-testid="home-signin-action"]').trigger("click");
    await flushPromises();
    expect(overlay.visible).toBe(true);
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("opens the sign-in activity page from the month calendar", async () => {
    const { wrapper, router, overlay } = await mountHome(false);
    await wrapper.get('[data-testid="home-signin-calendar"]').trigger("click");
    await flushPromises();
    expect(overlay.visible).toBe(true);
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("does not toast when the home banner slot is missing", async () => {
    adMock.mockResolvedValue(fail("ad.position.not-found", "广告位不存在"));
    const { wrapper } = await mountHome();
    expect(wrapper.find('[data-testid="ad-carousel"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="home-signin-card"]').exists()).toBe(true);
    expect(failToast).not.toHaveBeenCalled();
  });

  it("shows balance, streak and a month grid after login", async () => {
    signinListMock.mockResolvedValue(ok([{ activityId: 4, code: "daily", name: "每日签到" }]));
    signinCalendarMock.mockResolvedValue(
      ok({
        activityId: 4,
        activityCode: "daily",
        activityName: "每日签到",
        yearMonth: "2026-08",
        consecutiveDays: 3,
        catchupWindowDays: 1,
        catchupDailyLimit: 1,
        catchupCostPoints: 0,
        pointsBalance: 12,
        days: [{ date: "2026-08-20", state: "SIGNED" }],
        tiers: [],
      }),
    );
    const { wrapper, router } = await mountHome(true);
    expect(wrapper.get('[data-testid="home-points-value"]').text()).toBe("12");
    expect(wrapper.get('[data-testid="home-signin-streak"]').text()).toContain("3");
    expect(wrapper.get('[data-testid="month-cell-2026-08-20"]').text()).toBe("20");
    await wrapper.get('[data-testid="home-points-bar"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/mine/points");
  });

  it("does not toast or leave home when guest would otherwise 401 points/signin", async () => {
    pointsMock.mockResolvedValue(fail("auth.session.missing", "请先登录"));
    signinListMock.mockResolvedValue(fail("auth.session.missing", "请先登录"));
    const { wrapper, router } = await mountHome(false);
    expect(wrapper.get('[data-testid="home-points-login"]').text()).toContain(zhCN.home.pointsLogin);
    expect(failToast).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("does not render a standalone today-task catalog", async () => {
    listMock.mockResolvedValue(
      ok({
        total: 1,
        records: [{ taskId: 41, name: "浏览活动页", userStatus: "NOT_STARTED", rewardPreview: { firstName: "30积分", totalCount: 1 } }],
      }),
    );
    const { wrapper } = await mountHome();
    expect(wrapper.find('[data-testid="home-task-list"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain("浏览活动页");
    expect(listMock).not.toHaveBeenCalled();
  });

  it("uses a blue primary token", () => {
    expect(PORTAL_PRIMARY.toLowerCase()).not.toBe("#0f766e");
    expect(PORTAL_PRIMARY.toLowerCase()).not.toBe("#e11d48");
  });
});
