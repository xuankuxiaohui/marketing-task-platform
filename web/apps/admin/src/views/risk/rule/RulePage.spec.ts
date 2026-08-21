import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/risk", () => ({
  listRules: vi.fn(),
  updateRule: vi.fn(),
}));

import { listRules } from "@/api/risk";
import RulePage from "./index.vue";

const listRulesMock = vi.mocked(listRules);

describe("RiskRulePage", () => {
  beforeEach(() => {
    listRulesMock.mockReset();
    listRulesMock.mockResolvedValue(
      ok([
        {
          ruleCode: "R-f",
          enabled: true,
          threshold: 60,
          windowSeconds: 60,
          action: "REJECT",
        },
      ]),
    );
  });

  it("loads GET /admin/risk/rules", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = [PERMS.RISK_RULE_QUERY, PERMS.RISK_RULE_CONFIG];
    const wrapper = mount(RulePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    expect(listRulesMock).toHaveBeenCalled();
    expect(wrapper.text()).toContain("R-f");
  });
});
