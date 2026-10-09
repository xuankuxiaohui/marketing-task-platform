import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";
import type { PrizeCardView, PrizeListPage } from "@/api/prize";
import type { Result } from "@mkt/shared";

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
import PrizeCard from "@/components/PrizeCard.vue";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { submitOverlayLogin } from "@/test-utils/overlay-login";
import { TRACK, track } from "@/tracking";
import { showToast, Tabs } from "vant";
import { defineComponent, h } from "vue";
import MinePrizesPage from "./MinePrizesPage.vue";

const listMock = vi.mocked(fetchPrizeList);
const claimMock = vi.mocked(claimPrize);
const trackMock = vi.mocked(track);
const fetchCaptchaMock = vi.mocked(fetchCaptcha);
const loginMock = vi.mocked(login);

// Vant List writes its loading model before it emits load. A slot-only stub
// misses this ordering and allows handlers that block every real load event.
const ListStub = defineComponent({
  name: "VanList",
  props: {
    loading: Boolean,
    finished: Boolean,
    error: Boolean,
    errorText: { type: String, default: "" },
  },
  emits: ["update:loading", "update:error", "load"],
  setup(props, { emit, slots }) {
    function load(): void {
      emit("update:loading", true);
      emit("load");
    }
    function retry(): void {
      emit("update:error", false);
      load();
    }
    return () => h("div", [
      slots.default?.(),
      props.error
        ? h("button", { "data-testid": "prize-list-retry", onClick: retry }, props.errorText)
        : h("button", {
          "data-testid": "prize-load-more",
          disabled: props.loading || props.finished,
          onClick: load,
        }, "加载更多"),
    ]);
  },
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((accept, failPromise) => {
    resolve = accept;
    reject = failPromise;
  });
  return { promise, resolve, reject };
}

const PrizeHost = {
  name: "PrizeHost",
  setup() {
    return () => h("div", [h(MinePrizesPage), h(LoginOverlay)]);
  },
};

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
    activityId: 3,
    activityName: "夏季专题",
    ...overrides,
  };
}

async function mountPrizes(loggedIn = true) {
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
  const session = useSessionStore();
  session.clear();
  if (loggedIn) {
    session.setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  }
  const wrapper = mount(MinePrizesPage, {
    global: { plugins: [pinia, router], stubs: { "van-list": ListStub } },
  });
  await flushPromises();
  return { wrapper, router, session };
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
    listMock
      .mockResolvedValueOnce(ok({ total: 1, records: [prize()] }))
      .mockResolvedValue(ok({ total: 1, records: [prize({ status: "GRANTED", fulfillmentStatus: "ARRIVED" })] }));
    claimMock.mockResolvedValue(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    const { wrapper } = await mountPrizes();
    expect(wrapper.get('[data-testid="prize-action-11"]').text()).toBe(zhCN.prize.claim);
    expect(wrapper.get('[data-testid="prize-obtained-at"]').text()).toContain(zhCN.prize.obtainedAt);
    expect(wrapper.get('[data-testid="prize-activity"]').text()).toContain("3");
    expect(wrapper.get('[data-testid="prize-activity"]').text()).toContain("夏季专题");
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

  it("loads the next page after Vant sets loading and does not request a finished list", async () => {
    listMock
      .mockResolvedValueOnce(ok({ total: 2, records: [prize()] }))
      .mockResolvedValueOnce(ok({ total: 2, records: [prize({ recordId: 12 })] }));
    const { wrapper } = await mountPrizes();
    await wrapper.get('[data-testid="prize-load-more"]').trigger("click");
    await flushPromises();
    expect(listMock).toHaveBeenLastCalledWith({ tab: "ALL", page: 2, pageSize: 20 });
    expect(wrapper.findAll('[data-testid="prize-card"]')).toHaveLength(2);
    expect((wrapper.get('[data-testid="prize-load-more"]').element as HTMLButtonElement).disabled).toBe(true);
    await wrapper.get('[data-testid="prize-load-more"]').trigger("click");
    expect(listMock).toHaveBeenCalledTimes(2);
  });

  it("keeps earlier pages when loading fails and retries the same page", async () => {
    listMock
      .mockResolvedValueOnce(ok({ total: 2, records: [prize()] }))
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce(ok({ total: 2, records: [prize({ recordId: 12 })] }));
    const { wrapper } = await mountPrizes();
    await wrapper.get('[data-testid="prize-load-more"]').trigger("click");
    await flushPromises();
    expect(wrapper.findAll('[data-testid="prize-card"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="mine-prizes-empty"]').exists()).toBe(false);
    await wrapper.get('[data-testid="prize-list-retry"]').trigger("click");
    await flushPromises();
    expect(listMock.mock.calls.map(([query]) => query?.page)).toEqual([1, 2, 2]);
    expect(wrapper.findAll('[data-testid="prize-card"]')).toHaveLength(2);
  });

  it("shows an explicit first-page error with a retry instead of an empty prize list", async () => {
    listMock
      .mockResolvedValueOnce(fail("reward.list.failed", "服务暂不可用"))
      .mockResolvedValueOnce(ok({ total: 1, records: [prize()] }));
    const { wrapper } = await mountPrizes();
    expect(wrapper.get('[data-testid="mine-prizes-error"]').text()).toContain("服务暂不可用");
    expect(wrapper.find('[data-testid="mine-prizes-empty"]').exists()).toBe(false);
    await wrapper.get('[data-testid="mine-prizes-retry"]').trigger("click");
    await flushPromises();
    expect(listMock).toHaveBeenLastCalledWith({ tab: "ALL", page: 1, pageSize: 20 });
    expect(wrapper.findAll('[data-testid="prize-card"]')).toHaveLength(1);
  });

  it("ignores an older tab response after switching back to the current tab", async () => {
    const pending = deferred<Result<PrizeListPage>>();
    listMock
      .mockResolvedValueOnce(ok({ total: 1, records: [prize()] }))
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(ok({ total: 1, records: [prize({ recordId: 13, prizeName: "最新奖品" })] }));
    const { wrapper } = await mountPrizes();
    wrapper.getComponent(Tabs).vm.$emit("update:active", "PENDING");
    await flushPromises();
    expect(listMock).toHaveBeenLastCalledWith({ tab: "PENDING", page: 1, pageSize: 20 });
    expect(wrapper.findAll('[data-testid="prize-card"]')).toHaveLength(0);
    wrapper.getComponent(Tabs).vm.$emit("update:active", "ALL");
    await flushPromises();
    pending.resolve(ok({ total: 1, records: [prize({ recordId: 12, prizeName: "旧待领取奖品" })] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="prize-card"]').text()).toContain("最新奖品");
    expect(wrapper.text()).not.toContain("旧待领取奖品");
  });

  it("clears private rows at logout and rejects a former session response", async () => {
    const oldList = deferred<Result<PrizeListPage>>();
    listMock
      .mockReturnValueOnce(oldList.promise)
      .mockResolvedValueOnce(ok({ total: 1, records: [prize({ recordId: 12, prizeName: "新账号奖品" })] }));
    const { wrapper, session } = await mountPrizes();
    session.clear();
    await flushPromises();
    expect(wrapper.get('[data-testid="mine-prizes-login"]').text()).toContain(zhCN.session.missing);
    session.setLogin({ token: "client:new", userId: 10, nickname: "new" });
    await flushPromises();
    oldList.resolve(ok({ total: 1, records: [prize({ prizeName: "原账号奖品" })] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="prize-card"]').text()).toContain("新账号奖品");
    expect(wrapper.text()).not.toContain("原账号奖品");
  });

  it("reloads page one after a pending claim so shifted pagination cannot skip prizes", async () => {
    listMock
      .mockResolvedValueOnce(ok({ total: 0, records: [] }))
      .mockResolvedValueOnce(ok({ total: 3, records: [prize()] }))
      .mockResolvedValueOnce(ok({ total: 3, records: [prize({ recordId: 12 })] }))
      .mockResolvedValueOnce(ok({ total: 2, records: [prize({ recordId: 12 })] }))
      .mockResolvedValueOnce(ok({ total: 2, records: [prize({ recordId: 13 })] }));
    claimMock.mockResolvedValue(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    const { wrapper } = await mountPrizes();
    wrapper.getComponent(Tabs).vm.$emit("update:active", "PENDING");
    await flushPromises();
    await wrapper.get('[data-testid="prize-load-more"]').trigger("click");
    await flushPromises();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    await flushPromises();
    expect(listMock).toHaveBeenLastCalledWith({ tab: "PENDING", page: 1, pageSize: 20 });
    expect(wrapper.find('[data-testid="prize-action-11"]').exists()).toBe(false);
    await wrapper.get('[data-testid="prize-load-more"]').trigger("click");
    await flushPromises();
    expect(wrapper.findAll('[data-testid="prize-card"]')).toHaveLength(2);
    expect(listMock).toHaveBeenLastCalledWith({ tab: "PENDING", page: 2, pageSize: 20 });
  });

  it("does not toast, reload, or replace current prize rows for an old-session claim", async () => {
    const oldClaim = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    listMock
      .mockResolvedValueOnce(ok({ total: 1, records: [prize()] }))
      .mockResolvedValueOnce(ok({ total: 1, records: [prize({ recordId: 12, prizeName: "新账号奖品" })] }));
    claimMock.mockReturnValueOnce(oldClaim.promise);
    const { wrapper, session } = await mountPrizes();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    session.setLogin({ token: "client:new", userId: 10, nickname: "new" });
    await flushPromises();
    oldClaim.resolve(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    await flushPromises();
    expect(showToast).not.toHaveBeenCalled();
    expect(listMock).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="prize-card"]').text()).toContain("新账号奖品");
  });

  it("keeps a new-session claim busy when the old-session claim finishes", async () => {
    const oldClaim = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    const newClaim = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    listMock
      .mockResolvedValueOnce(ok({ total: 1, records: [prize()] }))
      .mockResolvedValueOnce(ok({ total: 1, records: [prize({ recordId: 12 })] }));
    claimMock.mockReturnValueOnce(oldClaim.promise).mockReturnValueOnce(newClaim.promise);
    const { wrapper, session } = await mountPrizes();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    session.setLogin({ token: "client:new", userId: 10, nickname: "new" });
    await flushPromises();
    expect(wrapper.getComponent(PrizeCard).props("claiming")).toBe(false);
    await wrapper.get('[data-testid="prize-action-12"]').trigger("click");
    expect(wrapper.getComponent(PrizeCard).props("claiming")).toBe(true);
    oldClaim.resolve(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    await flushPromises();
    expect(wrapper.getComponent(PrizeCard).props("claiming")).toBe(true);
    expect(showToast).not.toHaveBeenCalled();
    expect(claimMock).toHaveBeenCalledTimes(2);
    newClaim.resolve(fail("reward.claim.failed", "领取暂不可用"));
    await flushPromises();
    expect(wrapper.getComponent(PrizeCard).props("claiming")).toBe(false);
    expect(listMock).toHaveBeenCalledTimes(2);
  });

  it("does not show a claim result or reload after the page is unmounted", async () => {
    const pendingClaim = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    listMock.mockResolvedValue(ok({ total: 1, records: [prize()] }));
    claimMock.mockReturnValueOnce(pendingClaim.promise);
    const { wrapper } = await mountPrizes();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    wrapper.unmount();
    pendingClaim.resolve(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    await flushPromises();
    expect(showToast).not.toHaveBeenCalled();
    expect(listMock).toHaveBeenCalledTimes(1);
  });

  it("opens the login overlay for a guest without sending a private list request", async () => {
    const { wrapper } = await mountPrizes(false);
    expect(listMock).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="mine-prizes-login-action"]').trigger("click");
    expect(useLoginOverlayStore().pendingRedirect).toBe("/mine/prizes");
    expect(useLoginOverlayStore().visible).toBe(true);
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
