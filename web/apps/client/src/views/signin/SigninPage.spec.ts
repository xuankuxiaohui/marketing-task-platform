import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { ok } from "@/test-utils/result";

vi.mock("@/api/signin", () => ({
  fetchSigninActivities: vi.fn(),
  fetchSigninCalendar: vi.fn(),
  postCheckin: vi.fn(),
  postCatchup: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showConfirmDialog: vi.fn().mockResolvedValue(undefined),
    showSuccessToast: vi.fn(),
    showFailToast: vi.fn(),
  };
});

vi.mock("@/tracking", () => ({
  TRACK: { SIGNIN_PAGE_VIEW: "signin.page.view", SIGNIN_SIGN_CLICK: "signin.sign.click", SIGNIN_CATCHUP_CLICK: "signin.catchup.click" },
  track: vi.fn(),
}));

import { fetchSigninActivities, fetchSigninCalendar, postCatchup, postCheckin } from "@/api/signin";
import SigninPage from "./index.vue";

const activitiesMock = vi.mocked(fetchSigninActivities);
const calendarMock = vi.mocked(fetchSigninCalendar);
const checkinMock = vi.mocked(postCheckin);
const catchupMock = vi.mocked(postCatchup);

function calendarPayload(overrides: Record<string, unknown> = {}) {
  return {
    activityId: 1,
    activityCode: "daily_check",
    activityName: "每日签到",
    yearMonth: "2026-08",
    consecutiveDays: 2,
    catchupWindowDays: 7,
    catchupDailyLimit: 1,
    catchupCostPoints: 100,
    pointsBalance: 200,
    nextRewardHint: "再签 1 天可得",
    days: [{ date: "2026-08-20", state: "TODAY_AVAILABLE" }],
    tiers: [{ day: 1, prizeId: 10 }],
    ...overrides,
  };
}

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/signin", component: SigninPage },
      { path: "/mine", component: { template: "<div />" } },
    ],
  });
  await router.push("/signin");
  await router.isReady();
  const wrapper = mount(SigninPage, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("SigninPage", () => {
  beforeEach(() => {
    activitiesMock.mockReset();
    calendarMock.mockReset();
    checkinMock.mockReset();
    catchupMock.mockReset();
  });

  it("renders empty when no published activity", async () => {
    activitiesMock.mockResolvedValue(ok([]));
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="signin-empty"]').text()).toContain(zhCN.signin.empty);
  });

  it("renders calendar streak from backend states", async () => {
    activitiesMock.mockResolvedValue(ok([{ activityId: 1, code: "daily_check", name: "每日签到" }]));
    calendarMock.mockResolvedValue(ok(calendarPayload()));
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="signin-streak"]').text()).toContain("2");
    expect(wrapper.get('[data-testid="signin-hint"]').text()).toContain("再签");
    expect(wrapper.get('[data-testid="signin-checkin"]').text()).toContain(zhCN.signin.checkin);
  });

  it("greys catchup when balance is below cost", async () => {
    activitiesMock.mockResolvedValue(ok([{ activityId: 1, code: "daily_check", name: "每日签到" }]));
    calendarMock.mockResolvedValue(
      ok(
        calendarPayload({
          pointsBalance: 10,
          catchupCostPoints: 100,
          days: [
            { date: "2026-08-19", state: "MISSED_CATCHABLE" },
            { date: "2026-08-20", state: "TODAY_AVAILABLE" },
          ],
        }),
      ),
    );
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="signin-insufficient"]').text()).toContain(zhCN.signin.insufficient);
    expect(catchupMock).not.toHaveBeenCalled();
  });

  it("posts checkin from the today button", async () => {
    activitiesMock.mockResolvedValue(ok([{ activityId: 1, code: "daily_check", name: "每日签到" }]));
    calendarMock.mockResolvedValue(ok(calendarPayload()));
    checkinMock.mockResolvedValue(
      ok({ alreadySigned: false, recordId: 9, source: "CHECKIN", consecutiveDays: 1, rewards: [] }),
    );
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="signin-checkin"]').trigger("click");
    await flushPromises();
    expect(checkinMock).toHaveBeenCalledWith(1);
  });
});
