import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";
import type { PrizeCardView } from "@/api/prize";

vi.mock("@/api/prize", () => ({
  fetchPrizeList: vi.fn(),
  claimPrize: vi.fn(),
}));

vi.mock("@/api/auth", async () => {
  const actual = await vi.importActual<typeof import("@/api/auth")>("@/api/auth");
  return {
    ...actual,
    fetchCaptcha: vi.fn(),
    login: vi.fn(),
  };
});

vi.mock("@/tracking", () => ({
  TRACK: {
    REWARD_LIST_VIEW: "reward.list.view",
    REWARD_CLAIM_CLICK: "reward.claim.click",
  },
  track: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showFailToast: vi.fn(),
    showToast: vi.fn(),
  };
});

import type { CaptchaData, PortalAuthData } from "@/api/auth";
import { fetchCaptcha, login } from "@/api/auth";
import { claimPrize, fetchPrizeList } from "@/api/prize";
import LoginOverlay from "@/components/LoginOverlay.vue";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { submitOverlayLogin } from "@/test-utils/overlay-login";
import { TRACK, track } from "@/tracking";
import { showToast } from "vant";
import { defineComponent, h } from "vue";
import MinePrizesPage from "./MinePrizesPage.vue";

const listMock = vi.mocked(fetchPrizeList);
const claimMock = vi.mocked(claimPrize);
const trackMock = vi.mocked(track);
const fetchCaptchaMock = vi.mocked(fetchCaptcha);
const loginMock = vi.mocked(login);

const PrizeHost = defineComponent({
  name: "PrizeHost",
  setup() {
    return () => h("div", [h(MinePrizesPage), h(LoginOverlay)]);
  },
});

function prize(overrides: Partial<PrizeCardView> = {}): PrizeCardView {
  return {
    recordId: 11,
    prizeName: "积分礼包",
    prizeImage: "https://cdn.example/p.png",
    status: "WON",
    fulfillmentStatus: "NONE",
    expireAt: new Date(Date.now() + 120_000).toISOString(),
    sourceTaskId: 22,
    sourceTaskName: "每日浏览",
    obtainedAt: "2026-08-19T04:00:00.000Z",
    ...overrides,
  };
}

async function mountPrizes() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/mine/prizes", component: MinePrizesPage },
      { path: "/mine/prizes/:recordId", component: { template: "<div />" } },
      { path: "/home", component: { template: "<div />" } },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push("/mine/prizes");
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  const wrapper = mount(MinePrizesPage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("MinePrizesPage", () => {
  beforeEach(() => {
    listMock.mockReset();
    claimMock.mockReset();
    trackMock.mockReset();
    fetchCaptchaMock.mockReset();
    loginMock.mockReset();
    vi.mocked(showToast).mockReset();
    fetchCaptchaMock.mockResolvedValue(
      ok<CaptchaData>({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }),
    );
  });

  it("shows empty copy and a guide to tasks", async () => {
    listMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const { wrapper } = await mountPrizes();
    expect(wrapper.get('[data-testid="mine-prizes-empty"]').text()).toContain(zhCN.empty.prizes);
    expect(wrapper.get('[data-testid="empty-go-home"]').text()).toBe(zhCN.empty.goTasks);
    expect(trackMock).toHaveBeenCalledWith(TRACK.REWARD_LIST_VIEW, { tab: "ALL" });
  });

  it("lists 全部 before 待领取 and defaults to 全部", async () => {
    listMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const { wrapper } = await mountPrizes();
    const tabs = wrapper.findAll('[data-testid^="prize-tab-"]');
    expect(tabs[0]?.attributes("data-testid")).toBe("prize-tab-ALL");
    expect(tabs[1]?.attributes("data-testid")).toBe("prize-tab-PENDING");
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ tab: "ALL" }));
  });

  it("renders claimable WON with countdown and claims to 已到账", async () => {
    listMock.mockResolvedValue(ok({ total: 1, records: [prize()] }));
    claimMock.mockResolvedValue(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    const { wrapper } = await mountPrizes();
    expect(wrapper.get('[data-testid="prize-action-11"]').text()).toBe(zhCN.prize.claim);
    expect(wrapper.get('[data-testid="prize-obtained-at"]').text()).toContain(zhCN.prize.obtainedAt);
    expect(wrapper.get('[data-testid="prize-countdown"]').text()).toContain(zhCN.prize.remain);
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    await flushPromises();
    expect(trackMock).toHaveBeenCalledWith(TRACK.REWARD_CLAIM_CLICK, { recordId: 11 });
    expect(claimMock).toHaveBeenCalledWith(11);
    expect(showToast).toHaveBeenCalledWith(zhCN.prize.arrived);
    expect(wrapper.get('[data-testid="prize-action-11"]').text()).toBe(zhCN.prize.arrived);
  });

  it("opens prize detail from the icon and name row", async () => {
    listMock.mockResolvedValue(ok({ total: 1, records: [prize()] }));
    const { wrapper, router } = await mountPrizes();
    expect(wrapper.get('[data-testid="mine-list"]').classes()).toContain("mine-list");
    await wrapper.get('[data-testid="prize-open"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/mine/prizes/11");
  });

  it("keeps PERMANENT_FAILED gray with reason only", async () => {
    listMock.mockResolvedValue(
      ok({
        total: 1,
        records: [prize({ status: "PERMANENT_FAILED", failReason: "PRIZE_DISABLED", expireAt: undefined })],
      }),
    );
    const { wrapper } = await mountPrizes();
    const action = wrapper.get('[data-testid="prize-action-11"]');
    expect(action.text()).toContain("PRIZE_DISABLED");
    expect((action.element as HTMLButtonElement).disabled).toBe(true);
    expect(wrapper.get('[data-testid="prize-contact"]').text()).toBe(zhCN.prize.contact);
    await action.trigger("click");
    await flushPromises();
    expect(claimMock).not.toHaveBeenCalled();
  });

  it("surfaces claim errors without changing the button", async () => {
    listMock.mockResolvedValue(ok({ total: 1, records: [prize()] }));
    claimMock.mockResolvedValue(fail("reward.claim.expired", "奖品已过期"));
    const { wrapper } = await mountPrizes();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="prize-action-11"]').text()).toBe(zhCN.prize.claim);
  });

  it("loads prize cards after overlay login with only a redirect (no resume)", async () => {
    listMock.mockResolvedValue(ok({ total: 1, records: [prize()] }));
    loginMock.mockResolvedValue(ok<PortalAuthData>({ token: "client:t", userId: 9, nickname: "bob" }));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/mine/prizes", component: PrizeHost },
        { path: "/home", component: { template: "<div />" } },
        { path: "/register", component: { template: "<div />" } },
      ],
    });
    await router.push("/mine/prizes");
    await router.isReady();
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().clear();
    const wrapper = mount(PrizeHost, { global: { plugins: [pinia, router] } });
    await flushPromises();
    expect(listMock).not.toHaveBeenCalled();
    useLoginOverlayStore().request({ redirect: "/mine/prizes" });
    await flushPromises();
    await submitOverlayLogin(wrapper);
    expect(loginMock).toHaveBeenCalled();
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ tab: "ALL" }));
    expect(wrapper.get('[data-testid="prize-card"]').text()).toContain("积分礼包");
    expect(router.currentRoute.value.path).toBe("/mine/prizes");
    expect(useLoginOverlayStore().visible).toBe(false);
  });
});
