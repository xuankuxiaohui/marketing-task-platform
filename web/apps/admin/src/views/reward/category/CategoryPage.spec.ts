import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/reward", () => ({
  pageCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
  disableCategory: vi.fn(),
  enableCategory: vi.fn(),
}));

import { pageCategories } from "@/api/reward";
import RewardCategoryPage from "./index.vue";

const pageMock = vi.mocked(pageCategories);

describe("RewardCategoryPage list request lifecycle", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  beforeEach(() => {
    pageMock.mockReset();
  });

  it("ignores stale list results when a newer load wins", async () => {
    type PageCategoriesResult = Awaited<ReturnType<typeof pageCategories>>;
    let resolveFirst!: (value: PageCategoriesResult) => void;
    const first = new Promise<PageCategoriesResult>((resolve) => {
      resolveFirst = resolve;
    });
    pageMock
      .mockImplementationOnce(() => first)
      .mockResolvedValueOnce(
        ok({
          total: 1,
          records: [{ code: "newer", name: "新类目", status: "ENABLED" }],
        }),
      );

    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().$patch({ userId: 1, permissions: Object.values(PERMS) });
    const wrapper = mount(RewardCategoryPage, {
      global: { plugins: [pinia], directives: { auth } },
      attachTo: document.body,
    });

    // First mount load is in flight; trigger a second query before it resolves.
    await wrapper.get('[data-testid="category-query"]').trigger("click");
    await flushPromises();

    resolveFirst(
      ok({
        total: 1,
        records: [{ code: "stale", name: "旧类目", status: "ENABLED" }],
      }),
    );
    await flushPromises();

    expect(wrapper.get('[data-testid="category-table"]').text()).toContain("newer");
    expect(wrapper.get('[data-testid="category-table"]').text()).not.toContain("stale");
  });
});
