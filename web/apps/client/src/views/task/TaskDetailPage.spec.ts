import { enableAutoUnmount, flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";
import { deferred } from "@/test-utils/deferred";
import type { Result } from "@mkt/shared";
import type { PrizeCardView, PrizeListPage } from "@/api/prize";
import type { TaskDetailView } from "@/api/task";

vi.mock("@/api/task", () => ({
  fetchTaskDetail: vi.fn(),
  startTask: vi.fn(),
  clickTaskStep: vi.fn(),
  abandonTask: vi.fn(),
  INSTANCE_FROZEN_CODE: "task.instance.frozen",
}));

vi.mock("@/api/prize", () => ({
  fetchPrizeList: vi.fn(),
  claimPrize: vi.fn(),
}));

vi.mock("@/tracking", () => ({
  TRACK: {
    TASK_DETAIL_VIEW: "task.detail.view",
    TASK_START_CLICK: "task.start.click",
    TASK_STEP_CLICK: "task.step.click",
    TASK_COMPLETE_VIEW: "task.complete.view",
    TASK_ABANDON_CLICK: "task.abandon.click",
  },
  track: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showFailToast: vi.fn(),
    showToast: vi.fn(),
    showDialog: vi.fn().mockResolvedValue(undefined),
    showConfirmDialog: vi.fn().mockResolvedValue(undefined),
  };
});

import { fetchPrizeList } from "@/api/prize";
import { abandonTask, clickTaskStep, fetchTaskDetail, startTask } from "@/api/task";
import { showConfirmDialog, showDialog, showFailToast } from "vant";
import TaskDetailPage from "./TaskDetailPage.vue";

const prizeListMock = vi.mocked(fetchPrizeList);
const detailMock = vi.mocked(fetchTaskDetail);
const startMock = vi.mocked(startTask);
const clickMock = vi.mocked(clickTaskStep);
const abandonMock = vi.mocked(abandonTask);
const failToast = vi.mocked(showFailToast);
const dialog = vi.mocked(showDialog);

enableAutoUnmount(afterEach);

function taskPrize(overrides: Partial<PrizeCardView> = {}): PrizeCardView {
  return {
    recordId: 11,
    sourceTaskId: 5,
    prizeName: "积分礼包",
    status: "GRANTED",
    fulfillmentStatus: "ARRIVED",
    ...overrides,
  };
}

function inProgress(overrides: Partial<TaskDetailView> = {}): TaskDetailView {
  return {
    status: "IN_PROGRESS",
    instanceId: 77,
    rewardPreview: { firstName: "积分礼包", totalCount: 1 },
    steps: [
      { stepCode: "guide", name: "引导", type: "PASSIVE", status: "COMPLETED" },
      { stepCode: "click", name: "点击", type: "CLICK", status: "ACTIVE" },
      { stepCode: "report", name: "上报", type: "PROGRESS", status: "INACTIVE", progressCurrent: 1, progressTarget: 3 },
    ],
    currentStep: {
      stepCode: "click",
      name: "点击",
      type: "CLICK",
      action: { actionType: "NONE", buttonText: "去完成" },
    },
    ...overrides,
  };
}

async function mountDetail(taskId = 5) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/task/:taskId", component: TaskDetailPage },
      { path: "/home", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push(`/task/${taskId}`);
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  const wrapper = mount(TaskDetailPage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("TaskDetailPage", () => {
  beforeEach(() => {
    detailMock.mockReset();
    prizeListMock.mockReset();
    prizeListMock.mockResolvedValue(ok({ total: 0, records: [] }));
    startMock.mockReset();
    clickMock.mockReset();
    abandonMock.mockReset();
    failToast.mockReset();
    dialog.mockReset();
    dialog.mockResolvedValue(undefined);
    vi.mocked(showConfirmDialog).mockResolvedValue(undefined);
  });

  it("shows prize name, type and clock times when a grant exists", async () => {
    detailMock.mockResolvedValue(
      ok({
        status: "COMPLETED",
        instanceId: 77,
        task: { name: "每日浏览" },
      }),
    );
    prizeListMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            recordId: 11,
            prizeName: "积分礼包",
            categoryCode: "POINTS",
            expireAt: "2026-09-01T00:00:00.000Z",
            obtainedAt: "2026-08-19T04:00:00.000Z",
            claimedAt: "2026-08-20T04:00:00.000Z",
            activityId: 3,
            activityName: "夏季专题",
            sourceTaskId: 5,
            status: "GRANTED",
            fulfillmentStatus: "ARRIVED",
          },
        ],
      }),
    );
    const { wrapper } = await mountDetail();
    expect(wrapper.find('[data-testid="task-detail-ended"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="task-prize-name"]').text()).toBe("积分礼包");
    expect(wrapper.get('[data-testid="task-prize-type"]').text()).toContain("POINTS");
    expect(wrapper.get('[data-testid="task-prize-expire-at"]').text()).toContain(zhCN.prize.expireAt);
    expect(wrapper.get('[data-testid="task-prize-obtained-at"]').text()).toContain(zhCN.prize.obtainedAt);
    expect(wrapper.get('[data-testid="task-prize-claimed-at"]').text()).toContain(zhCN.prize.claimedAt);
    expect(wrapper.get('[data-testid="task-prize-activity"]').text()).toContain("夏季专题");
  });

  it("ignores an old account's prize response after switching accounts", async () => {
    const oldPrizes = deferred<Result<PrizeListPage>>();
    detailMock.mockResolvedValue(ok({ status: "COMPLETED", task: { name: "任务" } }));
    prizeListMock.mockReturnValueOnce(oldPrizes.promise)
      .mockResolvedValue(ok({ total: 1, records: [taskPrize({ prizeName: "新账号奖品" })] }));
    const { wrapper } = await mountDetail();

    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    expect(wrapper.get('[data-testid="task-prize-name"]').text()).toBe("新账号奖品");
    oldPrizes.resolve(ok({ total: 1, records: [taskPrize({ prizeName: "旧账号奖品" })] }));
    await flushPromises();
    expect(wrapper.text()).not.toContain("旧账号奖品");
    expect(wrapper.get('[data-testid="task-prize-name"]').text()).toBe("新账号奖品");
  });

  it("clears displayed prizes on logout and only fetches public task data", async () => {
    detailMock.mockResolvedValue(ok({ status: "COMPLETED", task: { name: "任务" } }));
    prizeListMock.mockResolvedValue(ok({ total: 1, records: [taskPrize()] }));
    const { wrapper } = await mountDetail();
    const guestDetail = deferred<Result<TaskDetailView>>();
    detailMock.mockReturnValueOnce(guestDetail.promise);

    useSessionStore().clear();
    await flushPromises();
    expect(wrapper.find('[data-testid="task-prize-facts"]').exists()).toBe(false);
    guestDetail.resolve(ok({ status: "NOT_STARTED", task: { name: "公开任务" } }));
    await flushPromises();
    expect(wrapper.text()).toContain("公开任务");
    expect(prizeListMock).toHaveBeenCalledTimes(1);
  });

  it("does not apply old task details or start a prize lookup under the new account", async () => {
    const oldDetail = deferred<Result<TaskDetailView>>();
    detailMock.mockReturnValueOnce(oldDetail.promise)
      .mockResolvedValue(ok({ status: "NOT_STARTED", task: { name: "新账号任务" } }));
    const { wrapper } = await mountDetail();
    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();

    oldDetail.resolve(ok(inProgress({ task: { name: "旧账号任务" } })));
    await flushPromises();
    expect(wrapper.text()).toContain("新账号任务");
    expect(wrapper.text()).not.toContain("旧账号任务");
    expect(prizeListMock).toHaveBeenCalledTimes(1);
  });

  it("ignores a pending prize response even if the original token is restored", async () => {
    const oldPrizes = deferred<Result<PrizeListPage>>();
    detailMock.mockResolvedValue(ok({ status: "COMPLETED" }));
    prizeListMock.mockReturnValueOnce(oldPrizes.promise);
    const { wrapper } = await mountDetail();
    const session = useSessionStore();
    session.clear();
    session.setLogin({ token: "client:t", userId: 9 });
    await flushPromises();

    oldPrizes.resolve(ok({ total: 1, records: [taskPrize({ prizeName: "失效请求奖品" })] }));
    await flushPromises();
    expect(wrapper.text()).not.toContain("失效请求奖品");
  });

  it("keeps new account prizes when an old prize request fails", async () => {
    const oldPrizes = deferred<Result<PrizeListPage>>();
    detailMock.mockResolvedValue(ok({ status: "COMPLETED", task: { name: "任务" } }));
    prizeListMock.mockReturnValueOnce(oldPrizes.promise)
      .mockResolvedValue(ok({ total: 1, records: [taskPrize({ prizeName: "新账号奖品" })] }));
    const { wrapper } = await mountDetail();
    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    oldPrizes.reject(new Error("old request failed"));
    await flushPromises();
    expect(wrapper.get('[data-testid="task-prize-name"]').text()).toBe("新账号奖品");
    expect(failToast).not.toHaveBeenCalled();
  });

  it("loads all matching task prizes beyond the first 50 global records", async () => {
    detailMock.mockResolvedValue(ok({ status: "COMPLETED", task: { name: "任务" } }));
    prizeListMock.mockImplementation(async (query) => ok({
      total: 52,
      records: query?.page === 2
        ? [taskPrize(), taskPrize({ recordId: 12, prizeName: "第二份奖品" })]
        : Array.from({ length: 50 }, (_, i) => taskPrize({ recordId: 100 + i, sourceTaskId: 6 })),
    }));
    const { wrapper } = await mountDetail();
    expect(wrapper.find('[data-testid="task-detail-ended"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-testid="task-prize-name"]').map((row) => row.text()))
      .toEqual(["积分礼包", "第二份奖品"]);
    expect(prizeListMock).toHaveBeenCalledTimes(2);
  });

  it("bounds scans by the initial total and deduplicates prizes repeated across pages", async () => {
    detailMock.mockResolvedValue(ok({ status: "COMPLETED", task: { name: "任务" } }));
    prizeListMock.mockResolvedValueOnce(ok({
      total: 51,
      records: [taskPrize(), ...Array.from({ length: 49 }, (_, i) => taskPrize({ recordId: 100 + i, sourceTaskId: 6 }))],
    })).mockResolvedValue(ok({ total: 1000, records: [taskPrize()] }));
    const { wrapper } = await mountDetail();
    expect(prizeListMock).toHaveBeenCalledTimes(2);
    expect(wrapper.findAll('[data-testid="task-prize-name"]')).toHaveLength(1);
  });

  it("stops an obsolete paged scan after switching tasks", async () => {
    const oldPage = deferred<Result<PrizeListPage>>();
    detailMock.mockResolvedValue(ok({ status: "COMPLETED", task: { name: "任务" } }));
    prizeListMock.mockReturnValueOnce(oldPage.promise)
      .mockResolvedValue(ok({ total: 1, records: [taskPrize({ sourceTaskId: 6, prizeName: "新任务奖品" })] }));
    const { wrapper, router } = await mountDetail();
    await router.push("/task/6");
    await flushPromises();
    oldPage.resolve(ok({ total: 100, records: Array.from({ length: 50 }, () => taskPrize()) }));
    await flushPromises();
    expect(wrapper.get('[data-testid="task-prize-name"]').text()).toBe("新任务奖品");
    expect(prizeListMock).toHaveBeenCalledTimes(2);
  });

  it("stops scanning on an empty page even when the reported total is larger", async () => {
    detailMock.mockResolvedValue(ok({ status: "COMPLETED", task: { name: "任务" } }));
    prizeListMock.mockResolvedValueOnce(ok({ total: 100, records: [taskPrize()] }))
      .mockResolvedValue(ok({ total: 100, records: [] }));
    const { wrapper } = await mountDetail();
    expect(prizeListMock).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="task-prize-name"]').text()).toBe("积分礼包");
  });

  it("retries a failed later prize page without displaying a partial result", async () => {
    detailMock.mockResolvedValue(ok({ status: "COMPLETED", task: { name: "任务" } }));
    const otherPrizes = Array.from({ length: 50 }, (_, i) => taskPrize({ recordId: 100 + i, sourceTaskId: 6 }));
    prizeListMock.mockResolvedValueOnce(ok({ total: 51, records: otherPrizes }))
      .mockResolvedValueOnce(fail("common.server-error", "奖品加载失败"));
    const { wrapper } = await mountDetail();
    expect(failToast).toHaveBeenCalledWith("奖品加载失败");
    expect(wrapper.find('[data-testid="task-prize-facts"]').exists()).toBe(false);

    prizeListMock.mockResolvedValueOnce(ok({ total: 51, records: otherPrizes }))
      .mockResolvedValueOnce(ok({ total: 51, records: [taskPrize()] }));
    await wrapper.get('[data-testid="task-detail-refresh"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="task-prize-name"]').text()).toBe("积分礼包");
    expect(prizeListMock.mock.calls.map(([query]) => query?.page)).toEqual([1, 2, 1, 2]);
  });

  it("keeps the latest manually refreshed details when older responses arrive last", async () => {
    detailMock.mockResolvedValueOnce(ok(inProgress()));
    const { wrapper } = await mountDetail();
    const oldRefresh = deferred<Result<TaskDetailView>>();
    detailMock.mockReturnValueOnce(oldRefresh.promise)
      .mockResolvedValueOnce(ok(inProgress({ task: { name: "最新详情" } })));
    await wrapper.get('[data-testid="task-detail-refresh"]').trigger("click");
    await wrapper.get('[data-testid="task-detail-refresh"]').trigger("click");
    await flushPromises();
    oldRefresh.resolve(ok(inProgress({ task: { name: "旧详情" } })));
    await flushPromises();
    expect(wrapper.text()).toContain("最新详情");
    expect(wrapper.text()).not.toContain("旧详情");
  });

  it("ignores old account completion feedback after switching accounts", async () => {
    const oldClick = deferred<Awaited<ReturnType<typeof clickTaskStep>>>();
    detailMock.mockResolvedValue(ok(inProgress()));
    clickMock.mockReturnValueOnce(oldClick.promise);
    const { wrapper } = await mountDetail();
    await wrapper.get('[data-testid="task-step-action"]').trigger("click");
    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    const detailCalls = detailMock.mock.calls.length;
    oldClick.resolve(ok({ instanceStatus: "COMPLETED", rewardFeedback: [{ prizeName: "旧账号奖品", count: 1 }] }));
    await flushPromises();
    expect(dialog).not.toHaveBeenCalled();
    expect(detailMock).toHaveBeenCalledTimes(detailCalls);
  });

  it("does not abandon the old instance after the account changes during confirmation", async () => {
    const confirmation = deferred<Awaited<ReturnType<typeof showConfirmDialog>>>();
    detailMock.mockResolvedValue(ok(inProgress()));
    vi.mocked(showConfirmDialog).mockReturnValueOnce(confirmation.promise);
    const { wrapper } = await mountDetail();
    await wrapper.get('[data-testid="task-abandon"]').trigger("click");
    useSessionStore().setLogin({ token: "client:new", userId: 10 });
    await flushPromises();
    confirmation.resolve(undefined);
    await flushPromises();
    expect(abandonMock).not.toHaveBeenCalled();
  });

  it("does not continue a prize scan after the component unmounts", async () => {
    const pendingPage = deferred<Result<PrizeListPage>>();
    detailMock.mockResolvedValue(ok({ status: "COMPLETED" }));
    prizeListMock.mockReturnValueOnce(pendingPage.promise);
    const { wrapper } = await mountDetail();
    wrapper.unmount();
    pendingPage.resolve(ok({ total: 100, records: Array.from({ length: 50 }, () => taskPrize()) }));
    await flushPromises();
    expect(prizeListMock).toHaveBeenCalledTimes(1);
  });

  it("shows 任务已结束 for OFFLINE without exposing a claim button", async () => {
    detailMock.mockResolvedValue(ok({ status: "OFFLINE" }));
    const { wrapper } = await mountDetail();
    expect(wrapper.get('[data-testid="task-detail-ended"]').text()).toContain(zhCN.task.ended);
    expect(wrapper.find('[data-testid="task-claim"]').exists()).toBe(false);
  });

  it("claims a not-started task then renders the in-progress timeline", async () => {
    detailMock
      .mockResolvedValueOnce(
        ok({
          status: "NOT_STARTED",
          task: { name: "每日浏览", description: "看一篇文章", iconUrl: "https://cdn.example/a.png" },
          stepsPreview: [{ seq: 1, name: "点击", type: "CLICK" }],
          rewardPreview: { firstName: "积分礼包", totalCount: 1 },
        }),
      )
      .mockResolvedValueOnce(ok(inProgress()));
    startMock.mockResolvedValue(ok({ instanceId: 77, instanceStatus: "IN_PROGRESS" }));
    const { wrapper } = await mountDetail();
    expect(wrapper.get('[data-testid="task-steps-preview"]').text()).toContain("点击");
    await wrapper.get('[data-testid="task-claim"]').trigger("click");
    await flushPromises();
    expect(startMock).toHaveBeenCalledWith(5);
    expect(wrapper.get('[data-testid="timeline-step-guide"]').attributes("data-tone")).toBe("done");
    expect(wrapper.get('[data-testid="timeline-step-click"]').attributes("data-tone")).toBe("current");
    expect(wrapper.get('[data-testid="timeline-progress-label"]').text()).toBe("1/3");
  });

  it("updates the timeline after a successful click and shows reward feedback on complete", async () => {
    detailMock
      .mockResolvedValueOnce(ok(inProgress()))
      .mockResolvedValueOnce(
        ok({
          status: "COMPLETED",
          instanceId: 77,
        }),
      );
    clickMock.mockResolvedValue(
      ok({
        instanceId: 77,
        stepStatus: "COMPLETED",
        instanceStatus: "COMPLETED",
        rewardFeedback: [{ prizeName: "积分礼包", count: 10 }],
      }),
    );
    const { wrapper } = await mountDetail();
    await wrapper.get('[data-testid="task-step-action"]').trigger("click");
    await flushPromises();
    expect(clickMock).toHaveBeenCalledWith(77, "click");
    expect(dialog).toHaveBeenCalledWith(
      expect.objectContaining({
        title: zhCN.task.rewardTitle,
        message: "积分礼包 × 10",
      }),
    );
    expect(wrapper.get('[data-testid="task-detail-ended"]').text()).toContain(zhCN.task.completed);
  });

  it("keeps 继续-style actions and toasts 账号受限 when click is frozen", async () => {
    detailMock.mockResolvedValue(ok(inProgress()));
    clickMock.mockResolvedValue(fail("task.instance.frozen", zhCN.task.frozen));
    const { wrapper } = await mountDetail();
    expect(wrapper.get('[data-testid="task-step-action"]').text()).toBe("去完成");
    await wrapper.get('[data-testid="task-step-action"]').trigger("click");
    await flushPromises();
    expect(failToast).toHaveBeenCalledWith(zhCN.task.frozen);
    expect(wrapper.get('[data-testid="task-step-action"]').text()).toBe("去完成");
    expect(wrapper.find('[data-testid="task-detail-ended"]').exists()).toBe(false);
  });

  it("abandons an in-progress instance after confirm", async () => {
    detailMock
      .mockResolvedValueOnce(ok(inProgress()))
      .mockResolvedValueOnce(ok({ status: "ABANDONED", instanceId: 77 }));
    abandonMock.mockResolvedValue(ok({ instanceStatus: "ABANDONED" }));
    const { wrapper } = await mountDetail();
    await wrapper.get('[data-testid="task-abandon"]').trigger("click");
    await flushPromises();
    expect(abandonMock).toHaveBeenCalledWith(77);
    expect(wrapper.get('[data-testid="task-detail-ended"]').text()).toContain(zhCN.task.abandoned);
  });

  it("reloads progress on the manual refresh entry and does not poll", async () => {
    detailMock
      .mockResolvedValueOnce(ok(inProgress()))
      .mockResolvedValueOnce(
        ok(
          inProgress({
            steps: [
              { stepCode: "guide", name: "引导", type: "PASSIVE", status: "COMPLETED" },
              { stepCode: "click", name: "点击", type: "CLICK", status: "COMPLETED" },
              { stepCode: "report", name: "上报", type: "PROGRESS", status: "ACTIVE", progressCurrent: 3, progressTarget: 3 },
            ],
            currentStep: { stepCode: "report", name: "上报", type: "PROGRESS", progressCurrent: 3, progressTarget: 3 },
          }),
        ),
      );
    const { wrapper } = await mountDetail();
    expect(detailMock).toHaveBeenCalledTimes(1);
    await wrapper.get('[data-testid="task-detail-refresh"]').trigger("click");
    await flushPromises();
    expect(detailMock).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="timeline-step-report"]').attributes("data-tone")).toBe("current");
    expect(wrapper.get('[data-testid="task-current-progress"]').text()).toBe("3/3");
  });
});
