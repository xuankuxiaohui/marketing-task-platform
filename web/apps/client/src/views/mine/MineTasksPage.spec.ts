import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";
import type { MineTaskPage } from "@/api/task";

vi.mock("@/api/task", () => ({
  fetchMineTasks: vi.fn(),
}));

vi.mock("@/api/activity", () => ({
  fetchActivities: vi.fn(),
  fetchActivityDetail: vi.fn(),
  postParticipate: vi.fn(),
}));

vi.mock("@/api/auth", async () => {
  const actual = await vi.importActual<typeof import("@/api/auth")>("@/api/auth");
  return {
    ...actual,
    fetchCaptcha: vi.fn(),
    login: vi.fn(),
  };
});

vi.mock("@/api/dict", async () => {
  const actual = await vi.importActual<typeof import("@/api/dict")>("@/api/dict");
  return {
    ...actual,
    fetchDict: vi.fn(),
  };
});

import type { CaptchaData, PortalAuthData } from "@/api/auth";
import { fetchCaptcha, login } from "@/api/auth";
import { fetchDict } from "@/api/dict";
import { fetchActivities } from "@/api/activity";
import { fetchMineTasks } from "@/api/task";
import LoginOverlay from "@/components/LoginOverlay.vue";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { submitOverlayLogin } from "@/test-utils/overlay-login";
import { defineComponent, h } from "vue";
import { DropdownItem, List, PullRefresh } from "vant";
import MineTasksPage from "./MineTasksPage.vue";

const mineMock = vi.mocked(fetchMineTasks);
const activityMock = vi.mocked(fetchActivities);
const dictMock = vi.mocked(fetchDict);
const fetchCaptchaMock = vi.mocked(fetchCaptcha);
const loginMock = vi.mocked(login);

const TasksHost = defineComponent({
  name: "TasksHost",
  setup() {
    return () => h("div", [h(MineTasksPage), h(LoginOverlay)]);
  },
});

async function mountMineTasks(loggedIn = true, actualList = false) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/mine/tasks", component: MineTasksPage },
      { path: "/home", component: { template: "<div />" } },
      { path: "/task/:taskId", component: { template: "<div />" } },
    ],
  });
  await router.push("/mine/tasks");
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.clear();
  if (loggedIn) {
    session.setLogin({ token: "client:t", userId: 9, nickname: "bob" });
  }
  const wrapper = mount(MineTasksPage, {
    global: { plugins: [pinia, router], stubs: actualList ? { "van-list": false } : {} },
  });
  await flushPromises();
  return { wrapper, router };
}

function deferredPage() {
  let resolve!: (value: ReturnType<typeof ok<MineTaskPage>>) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<ReturnType<typeof ok<MineTaskPage>>>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

function task(instanceId: number, taskName: string) {
  return { instanceId, taskId: instanceId, taskName, status: "IN_PROGRESS" };
}

describe("MineTasksPage", () => {
  beforeEach(() => {
    mineMock.mockReset();
    activityMock.mockReset();
    dictMock.mockReset();
    fetchCaptchaMock.mockReset();
    loginMock.mockReset();
    dictMock.mockResolvedValue(ok([]));
    activityMock.mockResolvedValue(ok([]));
    fetchCaptchaMock.mockResolvedValue(
      ok<CaptchaData>({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }),
    );
  });

  it("puts 全部 and 进行中 on one status row and has no 已放弃 tab", async () => {
    mineMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const { wrapper } = await mountMineTasks();
    const row = wrapper.get('[data-testid="mine-status-row"]');
    expect(row.find('[data-testid="mine-status-ALL"]').exists()).toBe(true);
    expect(row.find('[data-testid="mine-status-IN_PROGRESS"]').exists()).toBe(true);
    expect(row.find('[data-testid="mine-status-COMPLETED"]').exists()).toBe(true);
    expect(row.find('[data-testid="mine-status-EXPIRED"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="mine-status-ABANDONED"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-testid="mine-status-ALL"]').length).toBe(1);
    expect(wrapper.get('[data-testid="mine-list"]').classes()).toContain("mine-list");
  });

  it("shows in-progress empty copy and a guide to the home list", async () => {
    mineMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const { wrapper } = await mountMineTasks();
    expect(wrapper.get('[data-testid="mine-tasks-empty"]').text()).toContain(zhCN.empty.tasks);
    expect(wrapper.get('[data-testid="empty-go-home"]').text()).toBe(zhCN.empty.goHome);
  });

  it("requests COMPLETED and renders the row after the completed tab is selected", async () => {
    mineMock
      .mockResolvedValueOnce(ok({ total: 0, records: [] }))
      .mockResolvedValueOnce(
        ok({
          total: 1,
          records: [
            {
              instanceId: 9,
              taskId: 22,
              taskName: "demo-claim-01",
              status: "COMPLETED",
              startedAt: "2026-08-20T04:00:00Z",
            },
          ],
        }),
      );
    const { wrapper } = await mountMineTasks();
    expect(mineMock).toHaveBeenCalledWith(expect.objectContaining({ status: "IN_PROGRESS" }));
    (wrapper.vm as unknown as { selectStatus: (name: string) => void }).selectStatus("COMPLETED");
    await flushPromises();
    expect(mineMock).toHaveBeenCalledWith(expect.objectContaining({ status: "COMPLETED" }));
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("demo-claim-01");
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain(zhCN.task.completed);
  });

  it("omits status when 全部 is selected", async () => {
    mineMock
      .mockResolvedValueOnce(ok({ total: 0, records: [] }))
      .mockResolvedValueOnce(ok({ total: 0, records: [] }));
    const { wrapper } = await mountMineTasks();
    (wrapper.vm as unknown as { selectStatus: (name: string) => void }).selectStatus("ALL");
    await flushPromises();
    expect(mineMock).toHaveBeenCalledWith(expect.objectContaining({ status: undefined }));
  });

  it("does not fetch private lists for a guest", async () => {
    const { wrapper } = await mountMineTasks(false);
    expect(mineMock).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="mine-tasks-login"]').text()).toContain(zhCN.session.missing);
  });

  it("opens task detail from a mine row", async () => {
    activityMock.mockResolvedValue(
      ok([
        {
          id: 3,
          code: "summer",
          name: "夏季专题",
          submodules: [{ type: "TASK", refId: 22, sort: 1 }],
        },
      ]),
    );
    mineMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            instanceId: 4,
            taskId: 22,
            taskName: "每日浏览",
            status: "IN_PROGRESS",
            currentStepName: "点击",
            startedAt: "2026-08-20T04:00:00Z",
          },
        ],
      }),
    );
    const { wrapper, router } = await mountMineTasks();
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("每日浏览");
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("点击");
    expect(wrapper.get('[data-testid="mine-task-activity"]').text()).toContain("3");
    expect(wrapper.get('[data-testid="mine-task-activity"]').text()).toContain("夏季专题");
    await wrapper.get('[data-testid="mine-task-card"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/task/22");
  });

  it("loads mine task cards after overlay login with only a redirect (no resume)", async () => {
    mineMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            instanceId: 4,
            taskId: 22,
            taskName: "每日浏览",
            status: "IN_PROGRESS",
            currentStepName: "点击",
            startedAt: "2026-08-20T04:00:00Z",
          },
        ],
      }),
    );
    loginMock.mockResolvedValue(ok<PortalAuthData>({ token: "client:t", userId: 9, nickname: "bob" }));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/mine/tasks", component: TasksHost },
        { path: "/home", component: { template: "<div />" } },
        { path: "/register", component: { template: "<div />" } },
      ],
    });
    await router.push("/mine/tasks");
    await router.isReady();
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().clear();
    const wrapper = mount(TasksHost, { global: { plugins: [pinia, router] } });
    await flushPromises();
    expect(mineMock).not.toHaveBeenCalled();
    useLoginOverlayStore().request({ redirect: "/mine/tasks" });
    await flushPromises();
    await submitOverlayLogin(wrapper);
    expect(loginMock).toHaveBeenCalled();
    expect(mineMock).toHaveBeenCalledWith(expect.objectContaining({ status: "IN_PROGRESS" }));
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("每日浏览");
    expect(router.currentRoute.value.path).toBe("/mine/tasks");
    expect(useLoginOverlayStore().visible).toBe(false);
  });

  it("fetches page two through the actual Vant edge check", async () => {
    mineMock
      .mockResolvedValueOnce(ok({ total: 2, records: [task(1, "第一页")] }))
      .mockResolvedValueOnce(ok({ total: 2, records: [task(2, "第二页")] }));
    const { wrapper } = await mountMineTasks(true, true);
    const list = wrapper.getComponent(List);
    (list.element as HTMLElement).style.position = "fixed";
    (list.vm as unknown as { check: () => void }).check();
    await flushPromises();
    expect(list.emitted("update:loading")?.[0]).toEqual([true]);
    expect(list.emitted("load")).toHaveLength(1);
    expect(mineMock).toHaveBeenNthCalledWith(2, expect.objectContaining({ page: 2, pageSize: 20 }));
    expect(wrapper.findAll('[data-testid="mine-task-card"]')).toHaveLength(2);
    wrapper.unmount();
  });

  it("shows first-page error and retries without showing a false empty result", async () => {
    mineMock
      .mockResolvedValueOnce(fail("common.server-error", "读取失败"))
      .mockResolvedValueOnce(ok({ total: 1, records: [task(1, "恢复任务")] }));
    const { wrapper } = await mountMineTasks();
    expect(wrapper.get('[data-testid="mine-tasks-error"]').text()).toContain("读取失败");
    expect(wrapper.find('[data-testid="mine-tasks-empty"]').exists()).toBe(false);
    await wrapper.get('[data-testid="mine-tasks-retry"]').trigger("click");
    await flushPromises();
    expect(mineMock).toHaveBeenNthCalledWith(2, expect.objectContaining({ page: 1 }));
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("恢复任务");
    wrapper.unmount();
  });

  it("keeps the current rows on page-two failure and retries via actual Vant error text", async () => {
    mineMock
      .mockResolvedValueOnce(ok({ total: 2, records: [task(1, "第一页")] }))
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce(ok({ total: 2, records: [task(2, "第二页")] }));
    const { wrapper } = await mountMineTasks(true, true);
    const list = wrapper.getComponent(List);
    (list.element as HTMLElement).style.position = "fixed";
    (list.vm as unknown as { check: () => void }).check();
    await flushPromises();
    expect(wrapper.findAll('[data-testid="mine-task-card"]')).toHaveLength(1);
    expect(list.get(".van-list__error-text").text()).toContain(zhCN.common.networkError);
    await list.get(".van-list__error-text").trigger("click");
    await flushPromises();
    expect(mineMock).toHaveBeenNthCalledWith(3, expect.objectContaining({ page: 2 }));
    expect(wrapper.findAll('[data-testid="mine-task-card"]')).toHaveLength(2);
    wrapper.unmount();
  });

  it("filters by category and restarts the query from page one", async () => {
    dictMock.mockResolvedValue(ok([{ value: "DAILY", label: "每日任务" }]));
    mineMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const { wrapper } = await mountMineTasks();
    expect(wrapper.getComponent(DropdownItem).props("options")).toContainEqual({ text: "每日任务", value: "DAILY" });
    wrapper.getComponent(DropdownItem).vm.$emit("update:modelValue", "DAILY");
    await flushPromises();
    expect(mineMock).toHaveBeenLastCalledWith(expect.objectContaining({ category: "DAILY", page: 1 }));
    wrapper.unmount();
  });

  it("ignores a late previous status response", async () => {
    const old = deferredPage();
    mineMock.mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(ok({ total: 1, records: [task(2, "已完成的新查询")] }));
    const { wrapper } = await mountMineTasks();
    (wrapper.vm as unknown as { selectStatus: (name: string) => void }).selectStatus("COMPLETED");
    await flushPromises();
    old.resolve(ok({ total: 1, records: [task(1, "旧查询")] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("已完成的新查询");
    expect(wrapper.text()).not.toContain("旧查询");
    wrapper.unmount();
  });

  it("refreshes page one and ignores the pending pagination response", async () => {
    const pending = deferredPage();
    mineMock.mockResolvedValueOnce(ok({ total: 3, records: [task(1, "原任务")] }))
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(ok({ total: 1, records: [task(3, "刷新任务")] }));
    const { wrapper } = await mountMineTasks(true, true);
    const list = wrapper.getComponent(List);
    (list.element as HTMLElement).style.position = "fixed";
    (list.vm as unknown as { check: () => void }).check();
    await flushPromises();
    wrapper.getComponent(PullRefresh).vm.$emit("refresh");
    await flushPromises();
    pending.resolve(ok({ total: 3, records: [task(2, "晚到分页")] }));
    await flushPromises();
    expect(mineMock).toHaveBeenNthCalledWith(3, expect.objectContaining({ page: 1 }));
    expect(wrapper.findAll('[data-testid="mine-task-card"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("刷新任务");
    expect(wrapper.text()).not.toContain("晚到分页");
    wrapper.unmount();
  });

  it("reloads the dictionary after guest login and isolates replacement accounts", async () => {
    const { wrapper } = await mountMineTasks(false);
    expect(dictMock).not.toHaveBeenCalled();
    const old = deferredPage();
    mineMock.mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(ok({ total: 1, records: [task(2, "新账号任务")] }));
    dictMock.mockResolvedValue(ok([{ value: "DAILY", label: "每日任务" }]));
    const session = useSessionStore();
    session.setLogin({ token: "client:a", userId: 1 });
    await flushPromises();
    expect(dictMock).toHaveBeenCalled();
    expect(wrapper.getComponent(DropdownItem).props("options")).toContainEqual({ text: "每日任务", value: "DAILY" });
    session.setLogin({ token: "client:b", userId: 2 });
    await flushPromises();
    old.resolve(ok({ total: 1, records: [task(1, "旧账号任务")] }));
    await flushPromises();
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("新账号任务");
    session.clear();
    await flushPromises();
    expect(wrapper.find('[data-testid="mine-task-card"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="mine-tasks-login"]').text()).toContain(zhCN.session.missing);
    wrapper.unmount();
  });
});
