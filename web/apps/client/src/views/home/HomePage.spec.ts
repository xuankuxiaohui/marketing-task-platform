import { enableAutoUnmount, flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { PORTAL_PRIMARY } from "@/theme";
import { fail, ok } from "@/test-utils/result";
import { deferred } from "@/test-utils/deferred";
import type { Result } from "@mkt/shared";
import type { PortalActivityView } from "@/api/activity";
import type { SigninCalendarResponse } from "@/api/signin";
import { PORTAL_TOKEN_KEY } from "@/utils/token";

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
    showSuccessToast: vi.fn(),
  };
});

import { fetchActivities } from "@/api/activity";
import { fetchAdPosition } from "@/api/ad";
import { fetchPointsBalance } from "@/api/points";
import { fetchSigninActivities, fetchSigninCalendar, postCheckin } from "@/api/signin";
import { fetchTaskList } from "@/api/task";
import { PullRefresh, showFailToast, showSuccessToast } from "vant";
import HomePage from "./index.vue";

const activityMock = vi.mocked(fetchActivities);
const adMock = vi.mocked(fetchAdPosition);
const pointsMock = vi.mocked(fetchPointsBalance);
const listMock = vi.mocked(fetchTaskList);
const signinListMock = vi.mocked(fetchSigninActivities);
const signinCalendarMock = vi.mocked(fetchSigninCalendar);
const failToast = vi.mocked(showFailToast);

enableAutoUnmount(afterEach);

function calendar(consecutiveDays: number): SigninCalendarResponse {
  return {
    activityId: 4,
    activityCode: "daily",
    activityName: "每日签到",
    yearMonth: "2026-08",
    consecutiveDays,
    catchupWindowDays: 1,
    catchupDailyLimit: 1,
    catchupCostPoints: 0,
    pointsBalance: 0,
    days: [],
    tiers: [],
  };
}

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
  afterEach(() => {
    vi.useRealTimers();
  });

  beforeEach(() => {
    activityMock.mockReset();
    listMock.mockReset();
    failToast.mockReset();
    pointsMock.mockReset();
    signinListMock.mockReset();
    signinCalendarMock.mockReset();
    vi.mocked(postCheckin).mockReset();
    vi.mocked(showSuccessToast).mockReset();
    adMock.mockReset();
    adMock.mockResolvedValue(ok({ code: "home_banner", form: "CAROUSEL", materials: [] }));
    pointsMock.mockResolvedValue(ok({ balance: 12 }));
    listMock.mockResolvedValue(ok({ total: 0, records: [] }));
    signinListMock.mockResolvedValue(ok([]));
    activityMock.mockResolvedValue(ok([]));
  });

  it("keeps the guest hub on /home without routing to /login", async () => {
    const { wrapper, router, overlay } = await mountHome(false);
    expect(router.currentRoute.value.path).toBe("/home");
    expect(overlay.visible).toBe(false);
    expect(wrapper.get('[data-testid="home-signin-card"]').text()).toContain(zhCN.home.signin);
    expect(wrapper.find('[data-testid="home-signin-calendar"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="home-signin-week"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="signin-month-grid"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="home-activity-list"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="home-empty"]').text()).toContain(zhCN.home.empty);
    expect(wrapper.text()).toContain(zhCN.home.activities);
    expect(pointsMock).not.toHaveBeenCalled();
    expect(signinListMock).not.toHaveBeenCalled();
    expect(activityMock).toHaveBeenCalled();
    expect(listMock).not.toHaveBeenCalled();
  });

  it("opens overlay login from the compact sign-in action without leaving home", async () => {
    const { wrapper, router, overlay } = await mountHome(false);
    await wrapper.get('[data-testid="home-signin-action"]').trigger("click");
    await flushPromises();
    expect(overlay.visible).toBe(true);
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("opens overlay login from a week cell without leaving home", async () => {
    const { wrapper, router, overlay } = await mountHome(false);
    await wrapper.get('[data-testid="home-signin-week"] button').trigger("click");
    await flushPromises();
    expect(overlay.visible).toBe(true);
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("renders in-progress activity cards from the catalog", async () => {
    activityMock.mockResolvedValue(
      ok([{ id: 3, code: "summer", name: "夏季专题", startTime: "2026-08-01T00:00:00Z" }]),
    );
    const { wrapper, router } = await mountHome(false);
    expect(wrapper.find('[data-testid="home-activity-list"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="home-activity-3"]').text()).toContain("夏季专题");
    await wrapper.get('[data-testid="home-activity-3"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/activity");
    expect(router.currentRoute.value.query.id).toBe("3");
  });

  it("does not toast when the home banner slot is missing", async () => {
    adMock.mockResolvedValue(fail("ad.position.not-found", "广告位不存在"));
    const { wrapper } = await mountHome();
    expect(wrapper.find('[data-testid="ad-carousel"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="home-signin-card"]').exists()).toBe(true);
    expect(failToast).not.toHaveBeenCalled();
  });

  it("shows balance, streak and a week row after login", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-20T04:00:00.000Z"));
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
    expect(wrapper.get('[data-testid="week-cell-2026-08-20"]').text()).toContain("20");
    expect(wrapper.find('[data-testid="signin-month-grid"]').exists()).toBe(false);
    await wrapper.get('[data-testid="home-points-bar"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/mine/points");
    vi.useRealTimers();
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

  it("clears personal data on logout before the guest catalog returns", async () => {
    activityMock.mockResolvedValue(ok([{ id: 33, code: "targeted", name: "定向活动" }]));
    signinListMock.mockResolvedValue(ok([{ activityId: 4, code: "daily", name: "每日签到" }]));
    signinCalendarMock.mockResolvedValue(ok(calendar(7)));
    const { wrapper } = await mountHome(true);
    const guestActivities = deferred<Result<PortalActivityView[]>>();
    activityMock.mockReturnValueOnce(guestActivities.promise);

    useSessionStore().clear();
    await flushPromises();
    expect(wrapper.text()).not.toContain("定向活动");
    expect(wrapper.find('[data-testid="home-points-value"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="home-signin-streak"]').text()).not.toContain("7");
    guestActivities.resolve(ok([{ id: 3, code: "public", name: "公开活动" }]));
    await flushPromises();
    expect(wrapper.text()).toContain("公开活动");
    expect(pointsMock).toHaveBeenCalledTimes(1);
    expect(signinListMock).toHaveBeenCalledTimes(1);
  });

  it("reloads the catalog, balance and sign-in state for a replacement account", async () => {
    activityMock.mockResolvedValueOnce(ok([{ id: 33, code: "old", name: "旧账号活动" }]))
      .mockResolvedValue(ok([{ id: 34, code: "new", name: "新账号活动" }]));
    pointsMock.mockResolvedValueOnce(ok({ balance: 12 })).mockResolvedValue(ok({ balance: 99 }));
    signinListMock.mockResolvedValue(ok([{ activityId: 4, code: "daily", name: "每日签到" }]));
    signinCalendarMock.mockResolvedValueOnce(ok(calendar(7))).mockResolvedValue(ok(calendar(2)));
    const { wrapper } = await mountHome(true);

    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    expect(wrapper.text()).not.toContain("旧账号活动");
    expect(wrapper.text()).toContain("新账号活动");
    expect(wrapper.get('[data-testid="home-points-value"]').text()).toBe("99");
    expect(wrapper.get('[data-testid="home-signin-streak"]').text()).toContain("2");
  });

  it("ignores old account catalog, balance and calendar responses", async () => {
    const oldActivities = deferred<Result<PortalActivityView[]>>();
    const oldBalance = deferred<Awaited<ReturnType<typeof fetchPointsBalance>>>();
    const oldCalendar = deferred<Result<SigninCalendarResponse>>();
    activityMock.mockReturnValueOnce(oldActivities.promise)
      .mockResolvedValue(ok([{ id: 34, code: "new", name: "新账号活动" }]));
    pointsMock.mockReturnValueOnce(oldBalance.promise).mockResolvedValue(ok({ balance: 99 }));
    signinListMock.mockResolvedValue(ok([{ activityId: 4, code: "daily", name: "每日签到" }]));
    signinCalendarMock.mockReturnValueOnce(oldCalendar.promise).mockResolvedValue(ok(calendar(2)));
    const { wrapper } = await mountHome(true);
    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();

    oldActivities.resolve(ok([{ id: 33, code: "old", name: "旧账号活动" }]));
    oldBalance.resolve(ok({ balance: 12 }));
    oldCalendar.resolve(ok(calendar(7)));
    await flushPromises();
    expect(wrapper.text()).not.toContain("旧账号活动");
    expect(wrapper.text()).toContain("新账号活动");
    expect(wrapper.get('[data-testid="home-points-value"]').text()).toBe("99");
    expect(wrapper.get('[data-testid="home-signin-streak"]').text()).toContain("2");
  });

  it("does not fetch a calendar for an obsolete sign-in activity response", async () => {
    const oldSignin = deferred<Awaited<ReturnType<typeof fetchSigninActivities>>>();
    signinListMock.mockReturnValueOnce(oldSignin.promise).mockResolvedValue(ok([]));
    const { wrapper } = await mountHome(true);
    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    oldSignin.resolve(ok([{ activityId: 4, code: "old", name: "旧签到活动" }]));
    await flushPromises();
    expect(signinCalendarMock).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="home-signin-streak"]').text()).toContain(zhCN.home.pointsLogin);
  });

  it("clears targeted activity cards when another tab logs out", async () => {
    activityMock.mockResolvedValueOnce(ok([{ id: 33, code: "targeted", name: "定向活动" }]))
      .mockResolvedValue(ok([]));
    const { wrapper } = await mountHome(true);
    window.localStorage.removeItem(PORTAL_TOKEN_KEY);
    window.dispatchEvent(new StorageEvent("storage", {
      key: PORTAL_TOKEN_KEY, newValue: null, storageArea: window.localStorage,
    }));
    await flushPromises();
    expect(useSessionStore().authenticated).toBe(false);
    expect(wrapper.text()).not.toContain("定向活动");
  });

  it("preserves new data when an obsolete catalog request rejects", async () => {
    const oldActivities = deferred<Result<PortalActivityView[]>>();
    activityMock.mockReturnValueOnce(oldActivities.promise)
      .mockResolvedValue(ok([{ id: 34, code: "new", name: "新账号活动" }]));
    const { wrapper } = await mountHome(true);
    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    oldActivities.reject(new Error("old request failed"));
    await flushPromises();
    expect(wrapper.text()).toContain("新账号活动");
  });

  it("keeps the latest catalog when a previous refresh returns last", async () => {
    const { wrapper } = await mountHome(true);
    const oldActivities = deferred<Result<PortalActivityView[]>>();
    activityMock.mockReturnValueOnce(oldActivities.promise)
      .mockResolvedValue(ok([{ id: 34, code: "new", name: "最新活动" }]));
    wrapper.getComponent(PullRefresh).vm.$emit("refresh");
    wrapper.getComponent(PullRefresh).vm.$emit("refresh");
    await flushPromises();
    oldActivities.resolve(ok([{ id: 33, code: "old", name: "旧活动" }]));
    await flushPromises();
    expect(wrapper.text()).toContain("最新活动");
    expect(wrapper.text()).not.toContain("旧活动");
  });

  it("ignores old account check-in feedback after switching accounts", async () => {
    const oldCheckin = deferred<Awaited<ReturnType<typeof postCheckin>>>();
    signinListMock.mockResolvedValue(ok([{ activityId: 4, code: "daily", name: "每日签到" }]));
    signinCalendarMock.mockResolvedValue(ok(calendar(0)));
    vi.mocked(postCheckin).mockReturnValueOnce(oldCheckin.promise);
    const { wrapper } = await mountHome(true);
    await wrapper.get('[data-testid="home-signin-action"]').trigger("click");
    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    const pointsCalls = pointsMock.mock.calls.length;
    const signinCalls = signinListMock.mock.calls.length;
    oldCheckin.resolve(ok({ alreadySigned: false, recordId: 1, source: "NORMAL", consecutiveDays: 1, rewards: [] }));
    await flushPromises();
    expect(showSuccessToast).not.toHaveBeenCalled();
    expect(pointsMock).toHaveBeenCalledTimes(pointsCalls);
    expect(signinListMock).toHaveBeenCalledTimes(signinCalls);
  });

  it("does not continue sign-in loading after unmount", async () => {
    const pendingSignin = deferred<Awaited<ReturnType<typeof fetchSigninActivities>>>();
    signinListMock.mockReturnValueOnce(pendingSignin.promise);
    const { wrapper } = await mountHome(true);
    wrapper.unmount();
    pendingSignin.resolve(ok([{ activityId: 4, code: "daily", name: "每日签到" }]));
    await flushPromises();
    expect(signinCalendarMock).not.toHaveBeenCalled();
  });

  it("uses a blue primary token", () => {
    expect(PORTAL_PRIMARY.toLowerCase()).not.toBe("#0f766e");
    expect(PORTAL_PRIMARY.toLowerCase()).not.toBe("#e11d48");
  });
});
