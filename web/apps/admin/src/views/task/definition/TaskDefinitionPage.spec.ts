import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";
import { PUBLISH_REVISION_HINT } from "@/utils/publish-confirm";

vi.mock("@/api/task", () => ({
  pageDefinitions: vi.fn(),
  pageScheduleFailures: vi.fn(),
  publishDefinition: vi.fn(),
  scheduleDefinition: vi.fn(),
  cancelSchedule: vi.fn(),
  offlineDefinition: vi.fn(),
  copyDefinition: vi.fn(),
  deleteDefinition: vi.fn(),
}));

import { pageDefinitions, pageScheduleFailures, publishDefinition } from "@/api/task";
import TaskDefinitionPage from "./index.vue";

const pageMock = vi.mocked(pageDefinitions);
const failMock = vi.mocked(pageScheduleFailures);
const publishMock = vi.mocked(publishDefinition);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().$patch({ userId: 1, permissions: Object.values(PERMS) });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/task/definitions", component: TaskDefinitionPage },
      { path: "/task/definitions/edit/:id?", component: { template: "<div />" } },
      { path: "/task/definitions/:id/versions", component: { template: "<div />" } },
    ],
  });
  await router.push("/task/definitions");
  await router.isReady();
  const wrapper = mount(TaskDefinitionPage, {
    global: { plugins: [pinia, router], directives: { auth } },
  });
  await flushPromises();
  return wrapper;
}

describe("TaskDefinitionPage publish two-stage confirm", () => {
  beforeEach(() => {
    pageMock.mockReset();
    failMock.mockReset();
    publishMock.mockReset();
    failMock.mockResolvedValue(ok({ total: 0, records: [] }));
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [{ id: 8, code: "daily", name: "每日", status: "PUBLISHED", version: 2 }],
      }),
    );
  });

  it("previews impact then publishes with confirm=true", async () => {
    publishMock
      .mockResolvedValueOnce(
        ok({
          requiresConfirm: true,
          message: PUBLISH_REVISION_HINT,
          inFlightInstanceCount: 3,
          id: 8,
          code: "daily",
          version: 2,
          status: "PUBLISHED",
        }),
      )
      .mockResolvedValueOnce(ok({ requiresConfirm: false, id: 8, code: "daily", version: 3, status: "PUBLISHED" }));
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="task-publish"]').trigger("click");
    await flushPromises();
    expect(publishMock).toHaveBeenCalledWith(8, { confirm: false, early: undefined });
    expect(wrapper.get('[data-testid="confirm-message"]').text()).toContain(PUBLISH_REVISION_HINT);
    expect(wrapper.get('[data-testid="confirm-message"]').text()).toContain("在途实例 3");
    expect(publishMock).toHaveBeenCalledTimes(1);
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    await flushPromises();
    expect(publishMock).toHaveBeenNthCalledWith(2, 8, { confirm: true, early: undefined });
  });
});


describe("TaskDefinitionPage list request lifecycle", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  beforeEach(() => {
    pageMock.mockReset();
    failMock.mockReset();
    publishMock.mockReset();
    failMock.mockResolvedValue(ok({ total: 0, records: [] }));
  });

  it("ignores stale list results when a newer load wins", async () => {
    type PageDefinitionsResult = Awaited<ReturnType<typeof pageDefinitions>>;
    let resolveFirst!: (value: PageDefinitionsResult) => void;
    const first = new Promise<PageDefinitionsResult>((resolve) => {
      resolveFirst = resolve;
    });
    pageMock
      .mockImplementationOnce(() => first)
      .mockResolvedValueOnce(
        ok({
          total: 1,
          records: [{ id: 2, code: "newer", name: "新任务", status: "DRAFT", version: 1 }],
        }),
      );

    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().$patch({ userId: 1, permissions: Object.values(PERMS) });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/", component: { template: "<div />" } },
        { path: "/task/definitions", component: TaskDefinitionPage },
        { path: "/task/definitions/edit/:id?", component: { template: "<div />" } },
        { path: "/task/definitions/:id/versions", component: { template: "<div />" } },
      ],
    });
    await router.push("/task/definitions");
    await router.isReady();
    const wrapper = mount(TaskDefinitionPage, {
      global: { plugins: [pinia, router], directives: { auth } },
      attachTo: document.body,
    });

    // First mount load is in flight; trigger a second query before it resolves.
    await wrapper.get('[data-testid="task-query"]').trigger("click");
    await flushPromises();

    resolveFirst(
      ok({
        total: 1,
        records: [{ id: 1, code: "stale", name: "旧任务", status: "DRAFT", version: 1 }],
      }),
    );
    await flushPromises();

    expect(wrapper.get('[data-testid="task-table"]').text()).toContain("newer");
    expect(wrapper.get('[data-testid="task-table"]').text()).not.toContain("stale");
  });
});
