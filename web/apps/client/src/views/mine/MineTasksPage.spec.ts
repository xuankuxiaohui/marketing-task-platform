import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/task", () => ({
  fetchMineTasks: vi.fn(),
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
import { fetchMineTasks } from "@/api/task";
import LoginOverlay from "@/components/LoginOverlay.vue";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { submitOverlayLogin } from "@/test-utils/overlay-login";
import { defineComponent, h } from "vue";
import MineTasksPage from "./MineTasksPage.vue";

const mineMock = vi.mocked(fetchMineTasks);
const dictMock = vi.mocked(fetchDict);
const fetchCaptchaMock = vi.mocked(fetchCaptcha);
const loginMock = vi.mocked(login);

const TasksHost = defineComponent({
  name: "TasksHost",
  setup() {
    return () => h("div", [h(MineTasksPage), h(LoginOverlay)]);
  },
});

async function mountMineTasks(loggedIn = true) {
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
  const wrapper = mount(MineTasksPage, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("MineTasksPage", () => {
  beforeEach(() => {
    mineMock.mockReset();
    dictMock.mockReset();
    fetchCaptchaMock.mockReset();
    loginMock.mockReset();
    dictMock.mockResolvedValue(ok([]));
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
});
