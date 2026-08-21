import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { ok } from "@/test-utils/result";

vi.mock("@/api/task", () => ({
  fetchMineTasks: vi.fn(),
}));

vi.mock("@/api/dict", async () => {
  const actual = await vi.importActual<typeof import("@/api/dict")>("@/api/dict");
  return {
    ...actual,
    fetchDict: vi.fn(),
  };
});

import { fetchDict } from "@/api/dict";
import { fetchMineTasks } from "@/api/task";
import MineTasksPage from "./MineTasksPage.vue";

const mineMock = vi.mocked(fetchMineTasks);
const dictMock = vi.mocked(fetchDict);

async function mountMineTasks() {
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
  const wrapper = mount(MineTasksPage, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("MineTasksPage", () => {
  beforeEach(() => {
    mineMock.mockReset();
    dictMock.mockReset();
    dictMock.mockResolvedValue(ok([]));
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
    (wrapper.vm as unknown as { selectStatus: (name: string | number) => void }).selectStatus(1);
    await flushPromises();
    expect(mineMock).toHaveBeenCalledWith(expect.objectContaining({ status: "COMPLETED" }));
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain("demo-claim-01");
    expect(wrapper.get('[data-testid="mine-task-card"]').text()).toContain(zhCN.task.completed);
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
});
