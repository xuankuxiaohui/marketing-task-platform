import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";
import type { TaskCardView } from "@/api/task";

vi.mock("@/api/activity", () => ({
  fetchActivities: vi.fn(),
  fetchActivityDetail: vi.fn(),
  postParticipate: vi.fn(),
}));

vi.mock("@/api/task", () => ({
  fetchTaskList: vi.fn(),
  fetchTaskDetail: vi.fn(),
  startTask: vi.fn(),
  clickTaskStep: vi.fn(),
  abandonTask: vi.fn(),
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
  observeTaskCardExposure: vi.fn(() => () => undefined),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showSuccessToast: vi.fn(),
    showFailToast: vi.fn(),
    showToast: vi.fn(),
    showDialog: vi.fn().mockResolvedValue(undefined),
    showConfirmDialog: vi.fn().mockResolvedValue(undefined),
  };
});

import { fetchActivities, fetchActivityDetail } from "@/api/activity";
import { fetchTaskDetail, fetchTaskList } from "@/api/task";
import ActivityPage from "./index.vue";

const listMock = vi.mocked(fetchActivities);
const detailMock = vi.mocked(fetchActivityDetail);
const taskListMock = vi.mocked(fetchTaskList);
const taskDetailMock = vi.mocked(fetchTaskDetail);

function card(overrides: Partial<TaskCardView> = {}): TaskCardView {
  return {
    taskId: 8,
    taskCode: "t8",
    name: "每日浏览",
    category: "daily",
    iconUrl: "https://cdn.example/a.png",
    rewardPreview: { firstName: "积分礼包", totalCount: 1 },
    userStatus: "NOT_STARTED",
    sortWeight: 1,
    ...overrides,
  };
}

async function mountPage(query: Record<string, string> = {}, loggedIn = true) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/activity", component: ActivityPage },
      { path: "/signin", component: { template: "<div />" } },
      { path: "/task/:taskId", component: { template: "<div />" } },
      { path: "/mine", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push({ path: "/activity", query });
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.clear();
  if (loggedIn) {
    session.setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  }
  const wrapper = mount(ActivityPage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("ActivityPage", () => {
  beforeEach(() => {
    listMock.mockReset();
    detailMock.mockReset();
    taskListMock.mockReset();
    taskDetailMock.mockReset();
    taskListMock.mockResolvedValue(ok({ total: 0, records: [] }));
    taskDetailMock.mockResolvedValue(ok({ status: "NOT_STARTED" }));
  });

  it("renders empty when no published activity", async () => {
    listMock.mockResolvedValue(ok([]));
    const { wrapper } = await mountPage();
    expect(wrapper.get('[data-testid="activity-empty"]').text()).toContain(zhCN.activity.empty);
  });

  it("lazy-reveals rules on the side button and has no join CTA", async () => {
    listMock.mockResolvedValue(ok([{ id: 3, code: "summer", name: "夏季专题" }]));
    detailMock.mockResolvedValue(
      ok({
        id: 3,
        code: "summer",
        name: "夏季专题",
        richText: "<p>hello</p>",
        contentHash: "abc",
        version: 1,
        submodules: [{ type: "TASK", refId: 8, sort: 0 }],
      }),
    );
    taskListMock.mockResolvedValue(ok({ total: 1, records: [card()] }));
    const { wrapper } = await mountPage();
    expect(wrapper.get('[data-testid="activity-name"]').text()).toContain("夏季专题");
    expect(wrapper.find('[data-testid="activity-html"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="activity-join"]').exists()).toBe(false);
    await wrapper.get('[data-testid="activity-rules-btn"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="activity-html"]').html()).toContain("<p>hello</p>");
  });

  it("shows bound task cards and opens the half-sheet without leaving the page", async () => {
    listMock.mockResolvedValue(ok([{ id: 3, code: "summer", name: "夏季专题" }]));
    detailMock.mockResolvedValue(
      ok({
        id: 3,
        code: "summer",
        name: "夏季专题",
        richText: "<p>hello</p>",
        contentHash: "abc",
        version: 1,
        submodules: [
          { type: "TASK", refId: 8, sort: 0 },
          { type: "SIGNIN", refId: 1, sort: 1 },
        ],
      }),
    );
    taskListMock.mockResolvedValue(ok({ total: 2, records: [card(), card({ taskId: 99, name: "独立任务" })] }));
    taskDetailMock.mockResolvedValue(
      ok({
        status: "NOT_STARTED",
        task: { name: "每日浏览", description: "看一篇文章" },
        stepsPreview: [{ seq: 1, name: "点击", type: "CLICK" }],
      }),
    );
    const { wrapper, router } = await mountPage({ id: "3" });
    expect(wrapper.get('[data-testid="task-card"]').text()).toContain("每日浏览");
    expect(wrapper.text()).not.toContain("独立任务");
    expect(wrapper.find('[data-testid="activity-signin-card"]').exists()).toBe(false);
    await wrapper.get('[data-testid="task-card-open"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/activity");
    expect(wrapper.find('[data-testid="task-complete-sheet"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="task-claim"]').exists()).toBe(true);
  });

  it("does not show daily sign-in or a join result on a regular activity", async () => {
    listMock.mockResolvedValue(ok([{ id: 3, code: "summer", name: "夏季专题" }]));
    detailMock.mockResolvedValue(
      ok({
        id: 3,
        code: "summer",
        name: "夏季专题",
        richText: "<p>hello</p>",
        contentHash: "abc",
        version: 1,
        submodules: [{ type: "SIGNIN", refId: 4, sort: 0 }],
      }),
    );
    const { wrapper } = await mountPage({ id: "3" });
    expect(wrapper.find('[data-testid="activity-detail"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="activity-signin-card"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="activity-join"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="activity-result"]').exists()).toBe(false);
  });

  it("renders a cover image on the activity hero when the view has one", async () => {
    listMock.mockResolvedValue(ok([{ id: 3, code: "summer", name: "夏季专题" }]));
    detailMock.mockResolvedValue(
      ok({
        id: 3,
        code: "summer",
        name: "夏季专题",
        richText: "<p>hello</p>",
        contentHash: "abc",
        version: 1,
        submodules: [],
        coverUrl: "https://cdn.example/hero.png",
      }),
    );
    const { wrapper } = await mountPage({ id: "3" });
    expect(wrapper.get('[data-testid="activity-name"]').text()).toContain("夏季专题");
    expect(wrapper.get('[data-testid="fallback-image"]').attributes("src")).toBe("https://cdn.example/hero.png");
  });

});
