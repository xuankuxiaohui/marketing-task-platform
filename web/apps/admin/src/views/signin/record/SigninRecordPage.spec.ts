import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { visibleText } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("@/api/signin", () => ({
  pageSigninRecords: vi.fn(),
}));

import { pageSigninRecords } from "@/api/signin";
import SigninRecordPage from "./index.vue";

const pageMock = vi.mocked(pageSigninRecords);

describe("SigninRecordPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 9,
            activityId: 3,
            userId: 12,
            signDate: "2026-08-20",
            source: "CHECKIN",
            createdAt: "2026-08-20T04:00:00Z",
          },
        ],
      }),
    );
  });

  it("lists sign records", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const wrapper = mount(SigninRecordPage, { global: { plugins: [pinia] } });
    await flushPromises();
    expect(wrapper.get('[data-testid="signin-record-table"]').text()).toContain("CHECKIN");
    expect(visibleText(wrapper, "signin-record-query")).toContain(zhCN.common.query);
  });
});
