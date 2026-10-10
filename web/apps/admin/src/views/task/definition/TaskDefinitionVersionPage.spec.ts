import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/task", () => ({
  listVersions: vi.fn(),
  diffVersions: vi.fn(),
}));

import { diffVersions, listVersions } from "@/api/task";
import TaskDefinitionVersionPage from "./version.vue";

const listMock = vi.mocked(listVersions);
const diffMock = vi.mocked(diffVersions);

async function mountPage(id = "8") {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().$patch({ userId: 1, permissions: Object.values(PERMS) });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/task/definitions/:id/versions", component: TaskDefinitionVersionPage }],
  });
  await router.push(`/task/definitions/${id}/versions`);
  await router.isReady();
  const wrapper = mount(TaskDefinitionVersionPage, {
    global: { plugins: [pinia, router], directives: { auth } },
    attachTo: document.body,
  });
  await flushPromises();
  return { wrapper, router };
}

describe("TaskDefinitionVersionPage", () => {
  beforeEach(() => {
    listMock.mockReset();
    diffMock.mockReset();
    listMock.mockResolvedValue(
      ok([
        { version: 1, publishedAt: "2026-08-01T00:00:00Z" },
        { version: 2, publishedAt: "2026-08-02T00:00:00Z" },
      ]),
    );
  });

  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("lists versions for the task", async () => {
    const { wrapper } = await mountPage();
    expect(wrapper.get('[data-testid="version-table"]').text()).toContain("1");
    expect(wrapper.get('[data-testid="version-table"]').text()).toContain("2");
  });
});

describe("TaskDefinitionVersionPage request lifecycle", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  beforeEach(() => {
    listMock.mockReset();
    diffMock.mockReset();
  });

  it("ignores stale list results when a newer route id wins", async () => {
    type ListVersionsResult = Awaited<ReturnType<typeof listVersions>>;
    let resolveFirst!: (value: ListVersionsResult) => void;
    const first = new Promise<ListVersionsResult>((resolve) => {
      resolveFirst = resolve;
    });
    listMock
      .mockImplementationOnce(() => first)
      .mockResolvedValueOnce(ok([{ version: 9, publishedAt: "2026-08-09T00:00:00Z" }]));

    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().$patch({ userId: 1, permissions: Object.values(PERMS) });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/task/definitions/:id/versions", component: TaskDefinitionVersionPage }],
    });
    await router.push("/task/definitions/8/versions");
    await router.isReady();
    const wrapper = mount(TaskDefinitionVersionPage, {
      global: { plugins: [pinia, router], directives: { auth } },
      attachTo: document.body,
    });

    // First mount load is in flight; navigate to a newer id before it resolves.
    await router.push("/task/definitions/9/versions");
    await flushPromises();

    resolveFirst(ok([{ version: 1, publishedAt: "2026-08-01T00:00:00Z" }]));
    await flushPromises();

    const tableText = wrapper.get('[data-testid="version-table"]').text();
    expect(tableText).toContain("9");
    expect(tableText).not.toMatch(/\b1\b/);
  });

  it("ignores stale diff results when a newer diff wins", async () => {
    listMock.mockResolvedValue(ok([{ version: 1, publishedAt: "2026-08-01T00:00:00Z" }]));
    type DiffResult = Awaited<ReturnType<typeof diffVersions>>;
    let resolveFirst!: (value: DiffResult) => void;
    const first = new Promise<DiffResult>((resolve) => {
      resolveFirst = resolve;
    });
    diffMock
      .mockImplementationOnce(() => first)
      .mockResolvedValueOnce(ok({ steps: [{ op: "replace", key: "newer" }] }));

    const { wrapper } = await mountPage();
    await wrapper.get('[data-testid="diff-left"]').setValue("1");
    await wrapper.get('[data-testid="diff-right"]').setValue("2");
    await wrapper.get('[data-testid="diff-run"]').trigger("click");

    await wrapper.get('[data-testid="diff-right"]').setValue("3");
    await wrapper.get('[data-testid="diff-run"]').trigger("click");
    await flushPromises();

    resolveFirst(ok({ steps: [{ op: "replace", key: "stale" }] }));
    await flushPromises();

    expect(wrapper.get('[data-testid="diff-result"]').text()).toContain("newer");
    expect(wrapper.get('[data-testid="diff-result"]').text()).not.toContain("stale");
  });
});
