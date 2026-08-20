import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/system", () => ({
  pageAudits: vi.fn(),
}));

import { pageAudits } from "@/api/system";
import AuditPage from "./index.vue";

const pageAuditsMock = vi.mocked(pageAudits);

describe("AuditLogPage", () => {
  beforeEach(() => {
    pageAuditsMock.mockReset();
    pageAuditsMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 1,
            module: "identity",
            action: "admin-user-disable",
            operatorName: "admin",
            result: "SUCCESS",
            requestSummary: "{}",
            costMs: 12,
            traceId: "t-1",
            createdAt: "2026-08-20T00:00:00Z",
          },
        ],
      }),
    );
  });

  it("queries audits and has no delete control", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(AuditPage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    expect(wrapper.get('[data-testid="audit-table"]').text()).toContain("admin-user-disable");
    expect(wrapper.get('[data-testid="audit-no-delete"]').text()).toBe(zhCN.audit.noDelete);
    expect(wrapper.find('[data-testid="audit-delete"]').exists()).toBe(false);
    expect(wrapper.find(".row-actions").exists()).toBe(false);
  });
});
