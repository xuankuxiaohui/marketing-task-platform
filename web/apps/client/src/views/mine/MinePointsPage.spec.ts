import { enableAutoUnmount, flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Result } from "@mkt/shared";
import type { PointsBalanceView, PointsPortalTxView, PointsTxPage } from "@/api/points";
import { zhCN } from "@/locales/zh-CN";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/points", () => ({
  fetchPointsBalance: vi.fn(),
  fetchPointsTransactions: vi.fn(),
}));

vi.mock("@/api/auth", async () => {
  const actual = await vi.importActual<typeof import("@/api/auth")>("@/api/auth");
  return { ...actual, fetchCaptcha: vi.fn(), login: vi.fn() };
});

vi.mock("@/tracking", () => ({
  TRACK: { POINTS_PAGE_VIEW: "points.page.view" },
  track: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showFailToast: vi.fn(),
  };
});

import { DropdownItem, PullRefresh, showFailToast } from "vant";
import { fetchCaptcha, login } from "@/api/auth";
import { fetchPointsBalance, fetchPointsTransactions } from "@/api/points";
import LoginOverlay from "@/components/LoginOverlay.vue";
import { submitOverlayLogin } from "@/test-utils/overlay-login";
import { TRACK, track } from "@/tracking";
import MinePointsPage from "./MinePointsPage.vue";

const balanceMock = vi.mocked(fetchPointsBalance);
const txMock = vi.mocked(fetchPointsTransactions);
const trackMock = vi.mocked(track);

enableAutoUnmount(afterEach);

// Model the real Vant ordering: loading is updated before the load event.
const ListStub = {
  name: "VanList",
  props: { loading: Boolean, finished: Boolean, error: Boolean, errorText: { type: String, default: "" } },
  emits: ["update:loading", "update:error", "load"],
  template: `
    <div>
      <slot />
      <button v-if="error" data-testid="points-list-retry"
        @click="$emit('update:error', false); $emit('update:loading', true); $emit('load')">
        {{ errorText }}
      </button>
      <button v-else data-testid="points-load-more" :disabled="loading || finished"
        @click="$emit('update:loading', true); $emit('load')">加载更多</button>
    </div>
  `,
};

const PullRefreshStub = {
  name: "VanPullRefresh",
  props: { modelValue: Boolean },
  emits: ["update:modelValue", "refresh"],
  template: "<div><slot /></div>",
};

const PointsHost = {
  name: "PointsHost",
  components: { MinePointsPage, LoginOverlay },
  template: "<div><MinePointsPage /><LoginOverlay /></div>",
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((accept, failPromise) => {
    resolve = accept;
    reject = failPromise;
  });
  return { promise, resolve, reject };
}

function transaction(overrides: Partial<PointsPortalTxView> = {}): PointsPortalTxView {
  return {
    type: "EARN", amount: 10, balanceAfter: 30, sourceTaskId: 8,
    remark: "任务奖励", createdAt: "2026-08-20T04:00:00Z", ...overrides,
  };
}

async function mountPoints(sessionBalance = 0, loggedIn = true, withOverlay = false) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/mine/points", component: MinePointsPage },
      { path: "/home", component: { template: "<div />" } },
      { path: "/register", component: { template: "<div />" } },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push("/mine/points");
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().clear();
  if (loggedIn) {
    useSessionStore().setLogin({ token: "client:t", userId: 9, nickname: "bob" });
    useSessionStore().setPointsBalance(sessionBalance);
  }
  const wrapper = mount(withOverlay ? PointsHost : MinePointsPage, {
    global: { plugins: [pinia, router], stubs: { "van-list": ListStub, "van-pull-refresh": PullRefreshStub } },
  });
  await flushPromises();
  return { wrapper, router, session: useSessionStore() };
}

describe("MinePointsPage", () => {
  beforeEach(() => {
    balanceMock.mockReset();
    txMock.mockReset();
    trackMock.mockReset();
    vi.mocked(showFailToast).mockReset();
    vi.mocked(fetchCaptcha).mockReset();
    vi.mocked(login).mockReset();
    balanceMock.mockResolvedValue(ok({ balance: 0 }));
    txMock.mockResolvedValue(ok({ total: 0, records: [] }));
    vi.mocked(fetchCaptcha).mockResolvedValue(ok({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }));
  });

  it("shows empty ledger copy and refreshes balance on enter (R35.4)", async () => {
    balanceMock.mockResolvedValue(ok({ balance: 42 }));
    txMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const { wrapper, session } = await mountPoints(0);
    expect(trackMock).toHaveBeenCalledWith(TRACK.POINTS_PAGE_VIEW);
    expect(wrapper.get('[data-testid="points-balance"]').text()).toContain("42");
    expect(session.pointsBalance).toBe(42);
    expect(wrapper.get('[data-testid="mine-points-empty"]').text()).toContain(zhCN.empty.points);
  });

  it("renders signed amounts, running balance, and jumps to the source task", async () => {
    balanceMock.mockResolvedValue(ok({ balance: 30 }));
    txMock.mockResolvedValue(
      ok({
        total: 2,
        records: [
          {
            type: "EARN",
            amount: 10,
            balanceAfter: 30,
            sourceTaskId: 8,
            remark: "任务奖励",
            createdAt: "2026-08-20T04:00:00Z",
          },
          {
            type: "EXPIRE",
            amount: -5,
            balanceAfter: 20,
            createdAt: "2026-08-19T04:00:00Z",
          },
        ],
      }),
    );
    const { wrapper, router } = await mountPoints();
    const rows = wrapper.findAll('[data-testid="points-row"]');
    expect(rows[0]?.text()).toContain(zhCN.points.earn);
    expect(rows[0]?.text()).toContain("+10");
    expect(rows[0]?.text()).toContain("30");
    expect(rows[1]?.text()).toContain(zhCN.points.expire);
    expect(rows[1]?.text()).toContain("-5");
    await rows[0]?.trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/task/8");
  });

  it("guides an empty ledger to the task list and requests only once on enter", async () => {
    const { wrapper, router } = await mountPoints();
    expect(balanceMock).toHaveBeenCalledTimes(1);
    expect(txMock).toHaveBeenCalledTimes(1);
    await wrapper.get('[data-testid="empty-go-home"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("loads the next page after Vant sets loading and stops at the total", async () => {
    txMock
      .mockResolvedValueOnce(ok({ total: 2, records: [transaction()] }))
      .mockResolvedValueOnce(ok({ total: 2, records: [transaction({ remark: "第二笔奖励" })] }));
    const { wrapper } = await mountPoints();
    await wrapper.get('[data-testid="points-load-more"]').trigger("click");
    await flushPromises();
    expect(txMock).toHaveBeenLastCalledWith({ type: undefined, page: 2, pageSize: 20 });
    expect(wrapper.findAll('[data-testid="points-row"]')).toHaveLength(2);
    expect((wrapper.get('[data-testid="points-load-more"]').element as HTMLButtonElement).disabled).toBe(true);
    await wrapper.get('[data-testid="points-load-more"]').trigger("click");
    expect(txMock).toHaveBeenCalledTimes(2);
  });

  it("keeps existing pages on failure and retries the same next page", async () => {
    txMock
      .mockResolvedValueOnce(ok({ total: 2, records: [transaction()] }))
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce(ok({ total: 2, records: [transaction({ remark: "第二笔奖励" })] }));
    const { wrapper } = await mountPoints();
    await wrapper.get('[data-testid="points-load-more"]').trigger("click");
    await flushPromises();
    expect(wrapper.findAll('[data-testid="points-row"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="points-list-retry"]').text()).toBe(zhCN.common.networkError);
    expect(wrapper.find('[data-testid="mine-points-empty"]').exists()).toBe(false);
    await wrapper.get('[data-testid="points-list-retry"]').trigger("click");
    await flushPromises();
    expect(txMock.mock.calls.map(([query]) => query?.page)).toEqual([1, 2, 2]);
    expect(wrapper.findAll('[data-testid="points-row"]')).toHaveLength(2);
  });

  it("offers first-page retry without presenting a failure as an empty ledger", async () => {
    txMock
      .mockResolvedValueOnce(fail("common.server-error", "暂时无法读取流水"))
      .mockResolvedValueOnce(ok({ total: 1, records: [transaction()] }));
    const { wrapper } = await mountPoints();
    expect(wrapper.get('[data-testid="mine-points-error"]').text()).toContain("暂时无法读取流水");
    expect(wrapper.find('[data-testid="mine-points-empty"]').exists()).toBe(false);
    await wrapper.get('[data-testid="mine-points-retry"]').trigger("click");
    await flushPromises();
    expect(txMock).toHaveBeenLastCalledWith({ type: undefined, page: 1, pageSize: 20 });
    expect(wrapper.findAll('[data-testid="points-row"]')).toHaveLength(1);
  });

  it("filters from page one and ignores an older filter response", async () => {
    const old = deferred<Result<PointsTxPage>>();
    txMock
      .mockResolvedValueOnce(ok({ total: 1, records: [transaction()] }))
      .mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(ok({ total: 1, records: [transaction({ type: "CONSUME", remark: "新消耗流水" })] }));
    const { wrapper } = await mountPoints();
    expect(wrapper.getComponent(DropdownItem).props("options")?.[0]).toEqual({ text: zhCN.points.allTypes, value: "" });
    wrapper.getComponent(DropdownItem).vm.$emit("update:modelValue", "EARN");
    await flushPromises();
    expect(txMock).toHaveBeenLastCalledWith({ type: "EARN", page: 1, pageSize: 20 });
    expect(wrapper.findAll('[data-testid="points-row"]')).toHaveLength(0);
    wrapper.getComponent(DropdownItem).vm.$emit("update:modelValue", "CONSUME");
    await flushPromises();
    old.resolve(ok({ total: 1, records: [transaction({ remark: "旧获得流水" })] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="points-row"]').text()).toContain("新消耗流水");
    expect(wrapper.text()).not.toContain("旧获得流水");
    expect(txMock).toHaveBeenLastCalledWith({ type: "CONSUME", page: 1, pageSize: 20 });
    expect(balanceMock).toHaveBeenCalledTimes(1);
  });

  it("hides cached balance until the current balance request succeeds", async () => {
    const pending = deferred<Result<PointsBalanceView>>();
    balanceMock.mockReturnValueOnce(pending.promise);
    const { wrapper, session } = await mountPoints(999);
    expect(wrapper.get('[data-testid="points-balance-loading"]').text()).toBe(zhCN.common.loading);
    expect(wrapper.find('[data-testid="points-balance-amount"]').exists()).toBe(false);
    pending.resolve(ok({ balance: 42 }));
    await flushPromises();
    expect(wrapper.get('[data-testid="points-balance-amount"]').text()).toBe("42");
    expect(session.pointsBalance).toBe(42);
  });

  it("shows balance failure separately and retries without reloading the ledger", async () => {
    balanceMock
      .mockResolvedValueOnce(fail("common.server-error", "暂时无法读取余额"))
      .mockResolvedValueOnce(ok({ balance: 42 }));
    const { wrapper, session } = await mountPoints(999);
    expect(wrapper.get('[data-testid="points-balance-error"]').text()).toContain("暂时无法读取余额");
    expect(wrapper.find('[data-testid="points-balance-amount"]').exists()).toBe(false);
    await wrapper.get('[data-testid="points-balance-retry"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="points-balance-amount"]').text()).toBe("42");
    expect(session.pointsBalance).toBe(42);
    expect(txMock).toHaveBeenCalledTimes(1);
  });

  it("refreshes balance and ledger concurrently and waits for both", async () => {
    const nextBalance = deferred<Result<PointsBalanceView>>();
    const nextLedger = deferred<Result<PointsTxPage>>();
    balanceMock.mockResolvedValueOnce(ok({ balance: 30 })).mockReturnValueOnce(nextBalance.promise);
    txMock
      .mockResolvedValueOnce(ok({ total: 1, records: [transaction()] }))
      .mockReturnValueOnce(nextLedger.promise);
    const { wrapper, session } = await mountPoints();
    wrapper.getComponent(PullRefresh).vm.$emit("update:modelValue", true);
    wrapper.getComponent(PullRefresh).vm.$emit("refresh");
    await flushPromises();
    expect(balanceMock).toHaveBeenCalledTimes(2);
    expect(txMock).toHaveBeenCalledTimes(2);
    expect(txMock).toHaveBeenLastCalledWith({ type: undefined, page: 1, pageSize: 20 });
    expect(wrapper.get('[data-testid="points-row"]').text()).toContain("任务奖励");
    nextLedger.resolve(ok({ total: 1, records: [transaction({ remark: "刷新后流水" })] }));
    await flushPromises();
    expect(wrapper.getComponent(PullRefresh).props("modelValue")).toBe(true);
    nextBalance.resolve(ok({ balance: 50 }));
    await flushPromises();
    expect(wrapper.getComponent(PullRefresh).props("modelValue")).toBe(false);
    expect(wrapper.get('[data-testid="points-row"]').text()).toContain("刷新后流水");
    expect(session.pointsBalance).toBe(50);
  });

  it("rejects an older balance response after a newer refresh on the same account", async () => {
    const oldBalance = deferred<Result<PointsBalanceView>>();
    balanceMock.mockReturnValueOnce(oldBalance.promise).mockResolvedValueOnce(ok({ balance: 50 }));
    const { wrapper, session } = await mountPoints();
    wrapper.getComponent(PullRefresh).vm.$emit("refresh");
    await flushPromises();
    oldBalance.resolve(ok({ balance: 999 }));
    await flushPromises();
    expect(wrapper.get('[data-testid="points-balance-amount"]').text()).toBe("50");
    expect(session.pointsBalance).toBe(50);
  });

  it("clears private rows and balance on logout and ignores old-account success", async () => {
    const oldBalance = deferred<Result<PointsBalanceView>>();
    const oldLedger = deferred<Result<PointsTxPage>>();
    balanceMock.mockReturnValueOnce(oldBalance.promise).mockResolvedValueOnce(ok({ balance: 12 }));
    txMock
      .mockReturnValueOnce(oldLedger.promise)
      .mockResolvedValueOnce(ok({ total: 1, records: [transaction({ remark: "新账号流水" })] }));
    const { wrapper, session } = await mountPoints(999);
    session.clear();
    await flushPromises();
    expect(wrapper.get('[data-testid="mine-points-login"]').text()).toContain(zhCN.session.missing);
    expect(wrapper.find('[data-testid="points-balance-amount"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="points-balance"]').text()).toContain(zhCN.home.pointsLogin);
    expect(session.pointsBalance).toBe(0);
    session.setLogin({ token: "client:new", userId: 10, nickname: "new" });
    await flushPromises();
    oldBalance.resolve(ok({ balance: 999 }));
    oldLedger.resolve(ok({ total: 1, records: [transaction({ remark: "旧账号流水" })] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="points-row"]').text()).toContain("新账号流水");
    expect(wrapper.text()).not.toContain("旧账号流水");
    expect(wrapper.get('[data-testid="points-balance-amount"]').text()).toBe("12");
    expect(session.pointsBalance).toBe(12);
  });

  it("does not surface former-account failures after switching accounts", async () => {
    const oldBalance = deferred<Result<PointsBalanceView>>();
    const oldLedger = deferred<Result<PointsTxPage>>();
    balanceMock.mockReturnValueOnce(oldBalance.promise).mockResolvedValueOnce(ok({ balance: 12 }));
    txMock.mockReturnValueOnce(oldLedger.promise).mockResolvedValueOnce(ok({ total: 0, records: [] }));
    const { wrapper, session } = await mountPoints();
    session.setLogin({ token: "client:new", userId: 10, nickname: "new" });
    await flushPromises();
    oldBalance.reject(new Error("old account offline"));
    oldLedger.reject(new Error("old account offline"));
    await flushPromises();
    expect(wrapper.find('[data-testid="points-balance-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="mine-points-error"]').exists()).toBe(false);
    expect(showFailToast).not.toHaveBeenCalled();
    expect(session.pointsBalance).toBe(12);
  });

  it("does not write session balance after unmount", async () => {
    const pending = deferred<Result<PointsBalanceView>>();
    balanceMock.mockReturnValueOnce(pending.promise);
    const { wrapper, session } = await mountPoints(7);
    wrapper.unmount();
    pending.resolve(ok({ balance: 999 }));
    await flushPromises();
    expect(session.pointsBalance).toBe(7);
  });

  it("opens the login overlay without requesting guest balance or ledger", async () => {
    const { wrapper } = await mountPoints(0, false);
    expect(balanceMock).not.toHaveBeenCalled();
    expect(txMock).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="mine-points-empty"]').exists()).toBe(false);
    await wrapper.get('[data-testid="mine-points-login-action"]').trigger("click");
    expect(useLoginOverlayStore().pendingRedirect).toBe("/mine/points");
    expect(useLoginOverlayStore().visible).toBe(true);
  });

  it("loads balance and ledger after overlay login without leaving the page", async () => {
    balanceMock.mockResolvedValue(ok({ balance: 42 }));
    txMock.mockResolvedValue(ok({ total: 1, records: [transaction()] }));
    vi.mocked(login).mockResolvedValue(ok({ token: "client:new", userId: 10, nickname: "new" }));
    const { wrapper, router } = await mountPoints(0, false, true);
    await wrapper.get('[data-testid="mine-points-login-action"]').trigger("click");
    await flushPromises();
    await submitOverlayLogin(wrapper);
    expect(balanceMock).toHaveBeenCalledTimes(1);
    expect(txMock).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-testid="points-balance-amount"]').text()).toBe("42");
    expect(wrapper.get('[data-testid="points-row"]').text()).toContain("任务奖励");
    expect(router.currentRoute.value.path).toBe("/mine/points");
    expect(useLoginOverlayStore().visible).toBe(false);
  });
});
