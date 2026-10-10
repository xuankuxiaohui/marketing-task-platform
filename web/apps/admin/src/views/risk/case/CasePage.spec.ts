import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { setControl } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("@/api/risk", () => ({
  pageHits: vi.fn(),
  handleCase: vi.fn(),
}));

import { handleCase, pageHits } from "@/api/risk";
import CasePage from "./index.vue";

const pageMock = vi.mocked(pageHits);
const handleMock = vi.mocked(handleCase);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(CasePage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("RiskCasePage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    handleMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 21,
            hitType: "RULE",
            ruleCode: "R-a",
            userId: 9,
            dimensionValue: "9",
            hitValue: "12",
            threshold: "10",
            actionResult: "REJECTED",
            occurredAt: "2026-08-20T00:00:00Z",
          },
        ],
      }),
    );
    handleMock.mockResolvedValue(ok({ ok: true }));
  });

  it("loads hits and handles a case with required reason", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="hit-table"]').text()).toContain("R-a");
    await wrapper.get('[data-testid="case-handle"]').trigger("click");
    await setControl(wrapper, "handle-reason", "false-hit");
    await setControl(wrapper, "handle-action", "MARK_FALSE_POSITIVE");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(handleMock).toHaveBeenCalledWith({
      hitLogId: 21,
      userId: 9,
      action: "MARK_FALSE_POSITIVE",
      toWhitelist: false,
      reason: "false-hit",
      expireAt: undefined,
    });
  });
});


describe("RiskCasePage list request lifecycle", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  beforeEach(() => {
    pageMock.mockReset();
    handleMock.mockReset();
  });

  it("ignores stale list results when a newer load wins", async () => {
    type PageHitsResult = Awaited<ReturnType<typeof pageHits>>;
    let resolveFirst!: (value: PageHitsResult) => void;
    const first = new Promise<PageHitsResult>((resolve) => {
      resolveFirst = resolve;
    });
    pageMock
      .mockImplementationOnce(() => first)
      .mockResolvedValueOnce(
        ok({
          total: 1,
          records: [
            {
              id: 99,
              hitType: "RULE",
              ruleCode: "R-newer",
              userId: 2,
              dimensionValue: "2",
              hitValue: "5",
              threshold: "3",
              actionResult: "REJECTED",
              occurredAt: "2026-08-21T00:00:00Z",
            },
          ],
        }),
      );

    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().$patch({ userId: 1, permissions: Object.values(PERMS) });
    const wrapper = mount(CasePage, {
      global: { plugins: [pinia], directives: { auth } },
      attachTo: document.body,
    });

    // First mount load is in flight; trigger a second query before it resolves.
    await wrapper.get('[data-testid="case-query"]').trigger("click");
    await flushPromises();

    resolveFirst(
      ok({
        total: 1,
        records: [
          {
            id: 88,
            hitType: "RULE",
            ruleCode: "R-stale",
            userId: 1,
            dimensionValue: "1",
            hitValue: "9",
            threshold: "8",
            actionResult: "REJECTED",
            occurredAt: "2026-08-20T00:00:00Z",
          },
        ],
      }),
    );
    await flushPromises();

    expect(wrapper.get('[data-testid="hit-table"]').text()).toContain("R-newer");
    expect(wrapper.get('[data-testid="hit-table"]').text()).not.toContain("R-stale");
  });
});
