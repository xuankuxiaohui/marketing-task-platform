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

import { fetchTaskDetail, startTask } from "@/api/task";
import { showFailToast } from "vant";
import TaskCompleteSheet from "./TaskCompleteSheet.vue";

const detailMock = vi.mocked(fetchTaskDetail);
const startMock = vi.mocked(startTask);
const failToast = vi.mocked(showFailToast);

function notStarted(): TaskDetailView {
  return {
    status: "NOT_STARTED",
    task: { name: "每日浏览", description: "看一篇文章", iconUrl: "https://cdn.example/a.png" },
    stepsPreview: [{ seq: 1, name: "点击", type: "CLICK" }],
    rewardPreview: { firstName: "积分礼包", totalCount: 1 },
  };
}

function inProgress(): TaskDetailView {
  return {
    status: "IN_PROGRESS",
    instanceId: 77,
    rewardPreview: { firstName: "积分礼包", totalCount: 1 },
    steps: [
      { stepCode: "guide", name: "引导", type: "PASSIVE", status: "COMPLETED" },
      { stepCode: "click", name: "点击", type: "CLICK", status: "ACTIVE" },
    ],
    currentStep: {
      stepCode: "click",
      name: "点击",
      type: "CLICK",
      action: { actionType: "NONE", buttonText: "去完成" },
    },
  };
}

async function mountSheet(loggedIn = true) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/activity", component: { template: "<div />" } },
      { path: "/task/:taskId", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/activity");
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.clear();
  if (loggedIn) {
    session.setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  }
  const wrapper = mount(TaskCompleteSheet, {
    props: { show: true, taskId: 5 },
    global: { plugins: [pinia, router] },
  });
  await flushPromises();
  return { wrapper, router };
}

describe("TaskCompleteSheet", () => {
  beforeEach(() => {
    detailMock.mockReset();
    startMock.mockReset();
    failToast.mockReset();
  });

  it("opens as a half-height sheet and claims without changing the route", async () => {
    detailMock
      .mockResolvedValueOnce(ok(notStarted()))
      .mockResolvedValueOnce(ok(inProgress()));
    startMock.mockResolvedValue(ok({ instanceId: 77, instanceStatus: "IN_PROGRESS" }));
    const { wrapper, router } = await mountSheet();
    const sheet = wrapper.get('[data-testid="task-complete-sheet"]');
    expect(sheet.attributes("style") ?? "").toMatch(/60vh/);
    expect(wrapper.get('[data-testid="task-claim"]').text()).toBe(zhCN.task.claim);
    await wrapper.get('[data-testid="task-claim"]').trigger("click");
    await flushPromises();
    expect(startMock).toHaveBeenCalledWith(5);
    expect(router.currentRoute.value.path).toBe("/activity");
    expect(wrapper.get('[data-testid="task-step-action"]').text()).toBe("去完成");
  });

  it("sends a guest to login on claim without toasting 401", async () => {
    detailMock.mockResolvedValue(ok(notStarted()));
    const { wrapper, router } = await mountSheet(false);
    expect(wrapper.find('[data-testid="task-claim"]').exists()).toBe(true);
    await wrapper.get('[data-testid="task-claim"]').trigger("click");
    await flushPromises();
    expect(startMock).not.toHaveBeenCalled();
    expect(failToast).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/login");
    expect(router.currentRoute.value.query.redirect).toBe("/activity");
  });

  it("keeps 领取 after a generic risk block", async () => {
    detailMock.mockResolvedValue(ok(notStarted()));
    startMock.mockResolvedValue(fail("risk.blocked.generic", "暂时无法参与"));
    const { wrapper, router } = await mountSheet();
    await wrapper.get('[data-testid="task-claim"]').trigger("click");
    await flushPromises();
    expect(failToast).toHaveBeenCalledWith("暂时无法参与");
    expect(wrapper.get('[data-testid="task-claim"]').text()).toBe(zhCN.task.claim);
    expect(router.currentRoute.value.path).toBe("/activity");
  });
});
