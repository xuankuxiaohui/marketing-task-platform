import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";
import type { TaskDetailView } from "@/api/task";

vi.mock("@/api/task", () => ({
  fetchTaskDetail: vi.fn(),
  startTask: vi.fn(),
  clickTaskStep: vi.fn(),
  abandonTask: vi.fn(),
  INSTANCE_FROZEN_CODE: "task.instance.frozen",
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

import { abandonTask, clickTaskStep, fetchTaskDetail, startTask } from "@/api/task";
import { showConfirmDialog, showDialog, showFailToast } from "vant";
import TaskDetailPage from "./TaskDetailPage.vue";

const detailMock = vi.mocked(fetchTaskDetail);
const startMock = vi.mocked(startTask);
const clickMock = vi.mocked(clickTaskStep);
const abandonMock = vi.mocked(abandonTask);
const failToast = vi.mocked(showFailToast);
const dialog = vi.mocked(showDialog);

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
    startMock.mockReset();
    clickMock.mockReset();
    abandonMock.mockReset();
    failToast.mockReset();
    dialog.mockReset();
    dialog.mockResolvedValue(undefined);
    vi.mocked(showConfirmDialog).mockResolvedValue(undefined);
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
