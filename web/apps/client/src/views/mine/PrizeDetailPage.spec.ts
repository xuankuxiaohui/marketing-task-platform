import { enableAutoUnmount, flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import type { PrizeCardView, PrizeListPage } from "@/api/prize";
import type { Result } from "@mkt/shared";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { usePrizePreviewStore } from "@/store/prize-preview";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/prize", () => ({
  fetchPrizeList: vi.fn(),
  claimPrize: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return { ...actual, showToast: vi.fn(), showFailToast: vi.fn() };
});

import { Button, showFailToast, showToast } from "vant";
import { claimPrize, fetchPrizeList } from "@/api/prize";
import PrizeDetailPage from "./PrizeDetailPage.vue";

const claimMock = vi.mocked(claimPrize);
const listMock = vi.mocked(fetchPrizeList);

enableAutoUnmount(afterEach);

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((accept, failPromise) => {
    resolve = accept;
    reject = failPromise;
  });
  return { promise, resolve, reject };
}

function prize(overrides: Partial<PrizeCardView> = {}): PrizeCardView {
  return {
    recordId: 11,
    prizeName: "积分礼包",
    prizeImage: "https://cdn.example/p.png",
    status: "WON",
    fulfillmentStatus: "NONE",
    categoryCode: "POINTS",
    obtainedAt: "2026-08-19T04:00:00.000Z",
    expireAt: "2026-09-01T00:00:00.000Z",
    claimedAt: "2026-08-20T04:00:00.000Z",
    activityId: 3,
    activityName: "夏季专题",
    sourceTaskId: 22,
    sourceTaskName: "每日浏览",
    ...overrides,
  };
}

async function mountDetail(options: { cached?: PrizeCardView | null; loggedIn?: boolean; recordId?: number } = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/mine/prizes/:recordId", component: PrizeDetailPage },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push(`/mine/prizes/${options.recordId ?? 11}`);
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.clear();
  if (options.loggedIn !== false) {
    session.setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  }
  const preview = usePrizePreviewStore();
  preview.set(options.cached === undefined ? (session.authenticated ? prize() : null) : options.cached);
  const wrapper = mount(PrizeDetailPage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router, session, preview };
}

describe("PrizeDetailPage", () => {
  beforeEach(() => {
    claimMock.mockReset();
    listMock.mockReset();
    listMock.mockResolvedValue(ok({ total: 0, records: [] }));
    vi.mocked(showToast).mockReset();
    vi.mocked(showFailToast).mockReset();
  });

  it("renders cached prize fields and opens the source task", async () => {
    const { wrapper, router } = await mountDetail();
    expect(wrapper.get('[data-testid="prize-detail-name"]').text()).toBe("积分礼包");
    expect(wrapper.get('[data-testid="prize-type"]').text()).toContain("POINTS");
    expect(wrapper.get('[data-testid="prize-expire-at"]').text()).toContain(zhCN.prize.expireAt);
    expect(wrapper.get('[data-testid="prize-obtained-at"]').text()).toContain(zhCN.prize.obtainedAt);
    expect(wrapper.get('[data-testid="prize-claimed-at"]').text()).toContain(zhCN.prize.claimedAt);
    expect(wrapper.get('[data-testid="prize-activity"]').text()).toContain("3");
    expect(wrapper.get('[data-testid="prize-activity"]').text()).toContain("夏季专题");
    await wrapper.get('[data-testid="prize-source"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/task/22");
  });

  it("clears both cached and displayed prize data on an in-place account change", async () => {
    const pending = deferred<Result<PrizeListPage>>();
    listMock.mockReturnValueOnce(pending.promise);
    const { wrapper, session, preview } = await mountDetail();
    expect(listMock).not.toHaveBeenCalled();
    session.setLogin({ token: "client:new", userId: 10, nickname: "new" });
    expect(preview.prize).toBeNull();
    await flushPromises();
    expect(wrapper.find('[data-testid="prize-detail-name"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="prize-detail-loading"]').text()).toBe(zhCN.common.loading);
    pending.resolve(ok({ total: 0, records: [] }));
    await flushPromises();
    expect(wrapper.find('[data-testid="prize-detail"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="prize-detail-empty"]').exists()).toBe(true);
  });

  it("clears displayed data at logout and offers a login overlay without a private request", async () => {
    const { wrapper, session } = await mountDetail();
    session.clear();
    await flushPromises();
    expect(wrapper.find('[data-testid="prize-detail-name"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="prize-detail-login"]').text()).toContain(zhCN.session.missing);
    expect(listMock).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="prize-detail-login-action"]').trigger("click");
    expect(useLoginOverlayStore().pendingRedirect).toBe("/mine/prizes/11");
    expect(useLoginOverlayStore().visible).toBe(true);
  });

  it("reloads a guest detail after login and never requests guest prize data", async () => {
    const { wrapper, session } = await mountDetail({ loggedIn: false });
    expect(listMock).not.toHaveBeenCalled();
    session.setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    expect(listMock).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-testid="prize-detail-empty"]').exists()).toBe(true);
  });

  it("ignores an old-account lookup and its finally while the current lookup is pending", async () => {
    const old = deferred<Result<PrizeListPage>>();
    const current = deferred<Result<PrizeListPage>>();
    listMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
    const { wrapper, session } = await mountDetail({ cached: null });
    session.setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    old.resolve(ok({ total: 1, records: [prize({ prizeName: "旧账号奖品" })] }));
    await flushPromises();
    expect(wrapper.find('[data-testid="prize-detail-loading"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain("旧账号奖品");
    current.resolve(ok({ total: 0, records: [] }));
    await flushPromises();
    expect(wrapper.find('[data-testid="prize-detail-loading"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="prize-detail"]').exists()).toBe(false);
  });

  it("ignores former-account lookup failures after a current lookup completes", async () => {
    const old = deferred<Result<PrizeListPage>>();
    listMock.mockReturnValueOnce(old.promise).mockResolvedValueOnce(ok({ total: 0, records: [] }));
    const { wrapper, session } = await mountDetail({ cached: null });
    session.setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    old.reject(new Error("old failure"));
    await flushPromises();
    expect(wrapper.find('[data-testid="prize-detail-error"]').exists()).toBe(false);
    expect(showFailToast).not.toHaveBeenCalled();
  });

  it("loads a changed route record and ignores a late response for the previous record", async () => {
    const old = deferred<Result<PrizeListPage>>();
    listMock.mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(ok({ total: 1, records: [prize({ recordId: 12, prizeName: "新路线奖品" })] }));
    const { wrapper, router } = await mountDetail({ cached: null });
    await router.push("/mine/prizes/12");
    await flushPromises();
    old.resolve(ok({ total: 1, records: [prize({ prizeName: "旧路线奖品" })] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="prize-detail-name"]').text()).toBe("新路线奖品");
    expect(wrapper.text()).not.toContain("旧路线奖品");
    expect(listMock).toHaveBeenCalledTimes(2);
  });

  it("does not reuse a cached prize with a different route ID", async () => {
    const pending = deferred<Result<PrizeListPage>>();
    listMock.mockReturnValueOnce(pending.promise);
    const { wrapper, router } = await mountDetail();
    await router.push("/mine/prizes/12");
    await flushPromises();
    expect(wrapper.find('[data-testid="prize-detail-name"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="prize-detail-loading"]').exists()).toBe(true);
    pending.resolve(ok({ total: 1, records: [prize({ recordId: 12, prizeName: "第二个奖品" })] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="prize-detail-name"]').text()).toBe("第二个奖品");
  });

  it("shows lookup failure with retry and a loading state while retry is pending", async () => {
    const pending = deferred<Result<PrizeListPage>>();
    listMock.mockResolvedValueOnce(fail("common.server-error", "暂时无法读取奖品"))
      .mockReturnValueOnce(pending.promise);
    const { wrapper } = await mountDetail({ cached: null });
    expect(wrapper.get('[data-testid="prize-detail-error"]').text()).toContain("暂时无法读取奖品");
    expect(wrapper.find('[data-testid="prize-detail-empty"]').exists()).toBe(false);
    await wrapper.get('[data-testid="prize-detail-retry"]').trigger("click");
    await flushPromises();
    expect(wrapper.find('[data-testid="prize-detail-loading"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="prize-detail-empty"]').exists()).toBe(false);
    pending.resolve(ok({ total: 1, records: [prize()] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="prize-detail-name"]').text()).toBe("积分礼包");
  });

  it("claims once and updates both the current detail and its preview", async () => {
    const pending = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    claimMock.mockReturnValueOnce(pending.promise);
    const { wrapper, preview } = await mountDetail();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    expect(claimMock).toHaveBeenCalledTimes(1);
    expect(claimMock).toHaveBeenCalledWith(11);
    pending.resolve(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    await flushPromises();
    expect(wrapper.get('[data-testid="prize-action-11"]').text()).toBe(zhCN.prize.arrived);
    expect(preview.prize?.status).toBe("GRANTED");
    expect(showToast).toHaveBeenCalledWith(zhCN.prize.arrived);
  });

  it("does not write the preview or show a former-account claim response", async () => {
    const pending = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    claimMock.mockReturnValueOnce(pending.promise);
    const { wrapper, session, preview } = await mountDetail();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    session.setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    pending.resolve(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    await flushPromises();
    expect(preview.prize).toBeNull();
    expect(wrapper.find('[data-testid="prize-detail"]').exists()).toBe(false);
    expect(showToast).not.toHaveBeenCalled();
  });

  it("keeps the new route claim busy when a claim from the previous route finishes", async () => {
    const old = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    const current = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    claimMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
    listMock.mockResolvedValueOnce(ok({ total: 1, records: [prize({ recordId: 12, prizeName: "第二个奖品" })] }));
    const { wrapper, router } = await mountDetail();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    await router.push("/mine/prizes/12");
    await flushPromises();
    await wrapper.get('[data-testid="prize-action-12"]').trigger("click");
    expect(wrapper.getComponent(Button).props("loading")).toBe(true);
    old.resolve(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    await flushPromises();
    expect(wrapper.getComponent(Button).props("loading")).toBe(true);
    expect(wrapper.get('[data-testid="prize-detail-name"]').text()).toBe("第二个奖品");
    expect(showToast).not.toHaveBeenCalled();
    current.resolve(fail("reward.claim.failed", "领取暂不可用"));
    await flushPromises();
    expect(wrapper.getComponent(Button).props("loading")).toBe(false);
    expect(claimMock).toHaveBeenCalledTimes(2);
  });

  it("does not write claim results into the preview after unmount", async () => {
    const pending = deferred<Awaited<ReturnType<typeof claimPrize>>>();
    claimMock.mockReturnValueOnce(pending.promise);
    const { wrapper, preview } = await mountDetail();
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    wrapper.unmount();
    pending.resolve(ok({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }));
    await flushPromises();
    expect(preview.prize?.status).toBe("WON");
    expect(showToast).not.toHaveBeenCalled();
  });

  it("ignores a lookup failure after unmount", async () => {
    const pending = deferred<Result<PrizeListPage>>();
    listMock.mockReturnValueOnce(pending.promise);
    const { wrapper } = await mountDetail({ cached: null });
    wrapper.unmount();
    pending.reject(new Error("late failure"));
    await flushPromises();
    expect(showFailToast).not.toHaveBeenCalled();
  });

  it("finds a directly opened prize beyond the first 50 records and stops once found", async () => {
    const firstPage = Array.from({ length: 50 }, (_, index) => prize({ recordId: index + 1 }));
    const secondPage = Array.from({ length: 50 }, (_, index) => prize({
      recordId: index + 51,
      prizeName: `奖品${index + 51}`,
    }));
    listMock.mockResolvedValueOnce(ok({ total: 150, records: firstPage }))
      .mockResolvedValueOnce(ok({ total: 150, records: secondPage }));
    const { wrapper } = await mountDetail({ cached: null, recordId: 55 });
    expect(wrapper.get('[data-testid="prize-detail-name"]').text()).toBe("奖品55");
    expect(listMock.mock.calls.map(([query]) => query)).toEqual([
      { tab: "ALL", page: 1, pageSize: 50 },
      { tab: "ALL", page: 2, pageSize: 50 },
    ]);
  });

  it("stops looking at the total when the requested prize does not belong to the list", async () => {
    const firstPage = Array.from({ length: 50 }, (_, index) => prize({ recordId: index + 1 }));
    listMock.mockResolvedValueOnce(ok({ total: 51, records: firstPage }))
      .mockResolvedValueOnce(ok({ total: 51, records: [prize({ recordId: 51 })] }));
    const { wrapper } = await mountDetail({ cached: null, recordId: 99 });
    expect(wrapper.find('[data-testid="prize-detail-empty"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="prize-detail-error"]').exists()).toBe(false);
    expect(listMock).toHaveBeenCalledTimes(2);
  });

  it("does not continue an old-account multi-page lookup after the account changes", async () => {
    const oldSecondPage = deferred<Result<PrizeListPage>>();
    const firstPage = Array.from({ length: 50 }, (_, index) => prize({ recordId: index + 1 }));
    listMock.mockResolvedValueOnce(ok({ total: 150, records: firstPage }))
      .mockReturnValueOnce(oldSecondPage.promise)
      .mockResolvedValueOnce(ok({ total: 0, records: [] }));
    const { wrapper, session } = await mountDetail({ cached: null, recordId: 120 });
    expect(listMock).toHaveBeenCalledTimes(2);
    session.setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    oldSecondPage.resolve(ok({
      total: 150,
      records: Array.from({ length: 50 }, (_, index) => prize({ recordId: index + 51 })),
    }));
    await flushPromises();
    expect(listMock.mock.calls.map(([query]) => query?.page)).toEqual([1, 2, 1]);
    expect(wrapper.find('[data-testid="prize-detail-empty"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="prize-detail"]').exists()).toBe(false);
  });

  it("shows a later-page lookup failure and retries the lookup instead of claiming the prize is missing", async () => {
    const firstPage = Array.from({ length: 50 }, (_, index) => prize({ recordId: index + 1 }));
    listMock.mockResolvedValueOnce(ok({ total: 60, records: firstPage }))
      .mockRejectedValueOnce(new Error("later page offline"))
      .mockResolvedValueOnce(ok({ total: 60, records: firstPage }))
      .mockResolvedValueOnce(ok({ total: 60, records: [prize({ recordId: 55, prizeName: "恢复的奖品" })] }));
    const { wrapper } = await mountDetail({ cached: null, recordId: 55 });
    expect(wrapper.get('[data-testid="prize-detail-error"]').text()).toContain(zhCN.common.networkError);
    expect(wrapper.find('[data-testid="prize-detail-empty"]').exists()).toBe(false);
    await wrapper.get('[data-testid="prize-detail-retry"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="prize-detail-name"]').text()).toBe("恢复的奖品");
    expect(listMock.mock.calls.map(([query]) => query?.page)).toEqual([1, 2, 1, 2]);
  });
});
