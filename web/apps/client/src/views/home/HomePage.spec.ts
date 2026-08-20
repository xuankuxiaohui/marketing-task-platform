import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { fail, ok } from "@/test-utils/result";
import type { TaskCardView } from "@/api/task";

vi.mock("@/api/task", () => ({
  fetchTaskList: vi.fn(),
  startTask: vi.fn(),
  fetchMineTasks: vi.fn(),
  fetchTaskDetail: vi.fn(),
  clickTaskStep: vi.fn(),
  abandonTask: vi.fn(),
}));

vi.mock("@/tracking", () => ({
  TRACK: { TASK_START_CLICK: "task.start.click" },
  track: vi.fn(),
  observeTaskCardExposure: vi.fn(() => () => undefined),
}));

vi.mock("@/api/dict", async () => {
  const actual = await vi.importActual<typeof import("@/api/dict")>("@/api/dict");
  return {
    ...actual,
    fetchDict: vi.fn(),
  };
});

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showFailToast: vi.fn(),
    showToast: vi.fn(),
  };
});

import { fetchDict } from "@/api/dict";
import { fetchTaskList, startTask } from "@/api/task";
import { showFailToast } from "vant";
import HomePage from "./index.vue";

const listMock = vi.mocked(fetchTaskList);
const startMock = vi.mocked(startTask);
const dictMock = vi.mocked(fetchDict);
const failToast = vi.mocked(showFailToast);

function card(overrides: Partial<TaskCardView>): TaskCardView {
  return {
    taskId: 1,
    taskCode: "t1",
    name: "每日浏览",
    category: "daily",
    iconUrl: "https://cdn.example/a.png",
    badgeText: "热门",
    rewardPreview: { firstName: "积分礼包", totalCount: 2 },
    userStatus: "NOT_STARTED",
    sortWeight: 1,
    ...overrides,
  };
}

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/home", component: HomePage },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push("/home");
  await router.isReady();
  const wrapper = mount(HomePage, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("HomePage", () => {
  beforeEach(() => {
    listMock.mockReset();
    startMock.mockReset();
    dictMock.mockReset();
    failToast.mockReset();
    dictMock.mockResolvedValue(ok([{ label: "日常", value: "daily" }]));
  });

  it("shows the generic empty copy when the list is empty", async () => {
    listMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const { wrapper } = await mountHome();
    expect(wrapper.get('[data-testid="home-empty"]').text()).toContain(zhCN.home.empty);
  });

  it("renders claim / continue / terminal buttons from userStatus", async () => {
    listMock.mockResolvedValue(
      ok({
        total: 5,
        records: [
          card({ taskId: 1, userStatus: "NOT_STARTED" }),
          card({ taskId: 2, name: "进行中", userStatus: "IN_PROGRESS" }),
          card({ taskId: 3, name: "完成", userStatus: "COMPLETED" }),
          card({ taskId: 4, name: "放弃", userStatus: "ABANDONED" }),
          card({ taskId: 5, name: "过期", userStatus: "EXPIRED" }),
        ],
      }),
    );
    const { wrapper } = await mountHome();
    expect(wrapper.get('[data-testid="task-card-action-1"]').text()).toBe(zhCN.task.claim);
    expect(wrapper.get('[data-testid="task-card-action-2"]').text()).toBe(zhCN.task.continue);
    expect(wrapper.get('[data-testid="task-card-action-3"]').text()).toBe(zhCN.task.completed);
    expect(wrapper.get('[data-testid="task-card-action-4"]').text()).toBe(zhCN.task.abandoned);
    expect(wrapper.get('[data-testid="task-card-action-5"]').text()).toBe(zhCN.task.expired);
    expect(wrapper.get('[data-testid="task-card-reward"]').text()).toBe("积分礼包 等 2 项");
    expect((wrapper.get('[data-testid="task-card-action-3"]').element as HTMLButtonElement).disabled).toBe(true);
  });

  it("claims a not-started task then opens detail, and continues in-progress without start", async () => {
    listMock.mockResolvedValue(
      ok({
        total: 2,
        records: [
          card({ taskId: 11, userStatus: "NOT_STARTED" }),
          card({ taskId: 12, userStatus: "IN_PROGRESS" }),
        ],
      }),
    );
    startMock.mockResolvedValue(ok({ instanceId: 99, instanceStatus: "IN_PROGRESS" }));
    const { wrapper, router } = await mountHome();
    await wrapper.get('[data-testid="task-card-action-11"]').trigger("click");
    await flushPromises();
    expect(startMock).toHaveBeenCalledWith(11);
    expect(router.currentRoute.value.path).toBe("/task/11");

    await wrapper.get('[data-testid="task-card-action-12"]').trigger("click");
    await flushPromises();
    expect(startMock).toHaveBeenCalledTimes(1);
    expect(router.currentRoute.value.path).toBe("/task/12");
  });

  it("still renders 领取 after a generic risk block and does not hide the card", async () => {
    listMock.mockResolvedValue(ok({ total: 1, records: [card({ taskId: 8, userStatus: "NOT_STARTED" })] }));
    startMock.mockResolvedValue(fail("risk.blocked.generic", "暂时无法参与"));
    const { wrapper } = await mountHome();
    await wrapper.get('[data-testid="task-card-action-8"]').trigger("click");
    await flushPromises();
    expect(failToast).toHaveBeenCalledWith("暂时无法参与");
    expect(wrapper.get('[data-testid="task-card-action-8"]').text()).toBe(zhCN.task.claim);
  });
});
