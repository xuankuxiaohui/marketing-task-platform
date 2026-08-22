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

import { fetchActivities, fetchActivityDetail, postParticipate } from "@/api/activity";
import { fetchTaskDetail, fetchTaskList } from "@/api/task";
import ActivityPage from "./index.vue";

const listMock = vi.mocked(fetchActivities);
const detailMock = vi.mocked(fetchActivityDetail);
const joinMock = vi.mocked(postParticipate);
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
    joinMock.mockReset();
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

  it("renders sanitized html and participates", async () => {
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
    joinMock.mockResolvedValue(ok({ participationId: 9, result: "PASS", granted: true }));
    const { wrapper } = await mountPage();
    expect(wrapper.get('[data-testid="activity-name"]').text()).toContain("夏季专题");
    expect(wrapper.get('[data-testid="activity-html"]').html()).toContain("<p>hello</p>");
    await wrapper.get('[data-testid="activity-join"]').trigger("click");
    await flushPromises();
    expect(joinMock).toHaveBeenCalledWith(3);
    expect(wrapper.get('[data-testid="activity-result"]').text()).toContain("PASS");
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
    expect(wrapper.get('[data-testid="activity-signin-card"]').text()).toContain(zhCN.home.signin);
    await wrapper.get('[data-testid="task-card-open"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/activity");
    expect(wrapper.find('[data-testid="task-complete-sheet"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="task-claim"]').exists()).toBe(true);
  });

  it("sends a guest to login on participate without calling the write API", async () => {
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
      }),
    );
    const { wrapper, router } = await mountPage({ id: "3" }, false);
    expect(wrapper.find('[data-testid="activity-detail"]').exists()).toBe(true);
    await wrapper.get('[data-testid="activity-join"]').trigger("click");
    await flushPromises();
    expect(joinMock).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/login");
    expect(router.currentRoute.value.query.redirect).toBe("/activity?id=3");
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

  it("routes the activity sign-in card to the calendar", async () => {
    listMock.mockResolvedValue(ok([{ id: 3, code: "summer", name: "夏季专题" }]));
    detailMock.mockResolvedValue(
      ok({
        id: 3,
        code: "summer",
        name: "夏季专题",
        richText: "",
        contentHash: "abc",
        version: 1,
        submodules: [{ type: "SIGNIN", refId: 4, sort: 0 }],
      }),
    );
    const { wrapper, router } = await mountPage({ id: "3" });
    await wrapper.get('[data-testid="activity-signin-card"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/signin");
  });
});
