import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { RISK_RULE_LIST_CACHE_TTL_SECONDS } from "@/constants/risk";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { setWriteNotice } from "@/utils/feedback";
import { ok } from "@/test-utils/result";

vi.mock("@/api/risk", () => ({
  listRules: vi.fn(),
  updateRule: vi.fn(),
}));

import { listRules, updateRule } from "@/api/risk";
import RulePage from "./index.vue";

const listRulesMock = vi.mocked(listRules);
const updateRuleMock = vi.mocked(updateRule);

describe("RiskRulePage", () => {
  beforeEach(() => {
    listRulesMock.mockReset();
    updateRuleMock.mockReset();
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
    updateRuleMock.mockResolvedValue(
      ok({
        ruleCode: "R-f",
        enabled: true,
        threshold: 60,
        windowSeconds: 60,
        action: "REJECT",
      }),
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

  it("shows 规则编号 header (no bare code) and Chinese action labels; rule codes untranslated", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = [PERMS.RISK_RULE_QUERY, PERMS.RISK_RULE_CONFIG];
    const wrapper = mount(RulePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    const headers = wrapper.findAll("th").map((th) => th.text().trim());
    expect(headers).toContain(zhCN.rule.ruleCode);
    expect(zhCN.rule.ruleCode).toBe("规则编号");
    expect(headers).not.toContain("code");
    expect(wrapper.get('[data-testid="rule-action-cell"]').text()).toBe("拦截");
    expect(wrapper.text()).toContain("R-f");
    await wrapper.get('[data-testid="rule-edit"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="rule-action"]').text()).toContain("拦截");
    expect(wrapper.get('[data-testid="rule-action"]').text()).not.toContain("REJECT");
  });

  it("shows unknown action code as-is instead of blank", async () => {
    listRulesMock.mockResolvedValue(
      ok([{ ruleCode: "R-a", enabled: true, threshold: 1, windowSeconds: 60, action: "FUTURE_ACTION" as "REJECT" }]),
    );
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = [PERMS.RISK_RULE_QUERY];
    const wrapper = mount(RulePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    expect(wrapper.get('[data-testid="rule-action-cell"]').text()).toBe("FUTURE_ACTION");
  });

  it("shows cache TTL hint (N from LIST_ALL_TTL) in edit dialog", async () => {
    expect(RISK_RULE_LIST_CACHE_TTL_SECONDS).toBe(3);
    expect(zhCN.rule.cacheTtlHint).toContain(`${RISK_RULE_LIST_CACHE_TTL_SECONDS} 秒`);
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = [PERMS.RISK_RULE_QUERY, PERMS.RISK_RULE_CONFIG];
    const wrapper = mount(RulePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    await wrapper.get('[data-testid="rule-edit"]').trigger("click");
    await flushPromises();
    const hint = wrapper.get('[data-testid="rule-cache-ttl-hint"]');
    expect(hint.text()).toBe(zhCN.rule.cacheTtlHint);
  });

  it("toasts save success with TTL + cache-evict guidance", async () => {
    const success = vi.fn();
    setWriteNotice({ success, error: vi.fn() });
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = [PERMS.RISK_RULE_QUERY, PERMS.RISK_RULE_CONFIG];
    const wrapper = mount(RulePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    await wrapper.get('[data-testid="rule-edit"]').trigger("click");
    await flushPromises();
    await wrapper.get('[data-testid="form-submit"]').trigger("click");
    await flushPromises();
    expect(updateRuleMock).toHaveBeenCalled();
    expect(success).toHaveBeenCalledWith(zhCN.rule.savedEffective);
    expect(zhCN.rule.savedEffective).toContain(`约 ${RISK_RULE_LIST_CACHE_TTL_SECONDS} 秒内生效`);
    expect(zhCN.rule.savedEffective).toContain("缓存失效");
    setWriteNotice(null);
  });
});
