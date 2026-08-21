import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
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
