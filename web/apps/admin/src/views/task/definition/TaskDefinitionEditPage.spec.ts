import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@vue-flow/core", () => ({
  VueFlow: { name: "VueFlow", template: "<div />" },
}));

vi.mock("@/api/task", () => ({
  getDefinition: vi.fn(),
  pageMutexGroups: vi.fn(),
  saveDefinition: vi.fn(),
  publishDefinition: vi.fn(),
  resetRevision: vi.fn(),
  validateExpression: vi.fn(),
}));

import { getDefinition, pageMutexGroups, saveDefinition } from "@/api/task";
import TaskDefinitionEditPage from "./edit.vue";

const getMock = vi.mocked(getDefinition);
const mutexMock = vi.mocked(pageMutexGroups);
const saveMock = vi.mocked(saveDefinition);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().$patch({ userId: 1, permissions: Object.values(PERMS) });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/task/definitions/edit/:id?", component: TaskDefinitionEditPage }],
  });
  await router.push("/task/definitions/edit/8");
  await router.isReady();
  const wrapper = mount(TaskDefinitionEditPage, {
    global: {
      plugins: [pinia, router],
      directives: { auth },
      stubs: { TaskCanvasPanel: { template: "<div data-testid=\"task-canvas-stub\" />" } },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("TaskDefinitionEditPage canvas", () => {
  beforeEach(() => {
    getMock.mockReset();
    mutexMock.mockReset();
    saveMock.mockReset();
    mutexMock.mockResolvedValue(ok({ total: 0, records: [] }));
    getMock.mockResolvedValue(
      ok({
        id: 8,
        code: "daily",
        name: "每日",
        cycleType: "DAILY",
        status: "DRAFT",
        version: 0,
        pendingRevision: false,
        steps: [
          { code: "go", name: "浏览", seq: 1, type: "CLICK" },
          { code: "reward", name: "发奖", seq: 2, type: "REWARD", prizeId: 3 },
        ],
        transitions: [{ fromStepCode: "go", toStepCode: "reward", priority: 0 }],
      }),
    );
    saveMock.mockResolvedValue(ok({ id: 8, code: "daily", version: 0, status: "DRAFT" }));
  });

  it("renders vue-flow canvas nodes from aggregate steps and saves them", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="task-canvas-steps"]').text()).toContain("go");
    expect(wrapper.get('[data-testid="task-canvas-steps"]').text()).toContain("reward");
    await wrapper.get('[data-testid="step-code"]').setValue("done");
    await wrapper.get('[data-testid="step-name"]').setValue("完成");
    await wrapper.get('[data-testid="step-add"]').trigger("click");
    await wrapper.get('[data-testid="task-save"]').trigger("click");
    await flushPromises();
    expect(saveMock).toHaveBeenCalled();
    const body = saveMock.mock.calls[0]?.[0];
    expect(body?.steps?.map((step) => step.code)).toEqual(["go", "reward", "done"]);
    expect(body?.transitions?.[0]?.fromStepCode).toBe("go");
  });
});
