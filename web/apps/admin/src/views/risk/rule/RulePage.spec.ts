import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import RulePage from "./index.vue";

describe("RiskRulePage", () => {
  it("does not invent GET/PUT /admin/risk/rules", () => {
    const wrapper = mount(RulePage);
    expect(wrapper.get('[data-testid="rule-no-api"]').text()).toBe(zhCN.rule.noApiHint);
    expect(wrapper.find('[data-testid="rule-save"]').exists()).toBe(false);
    expect(wrapper.find("form").exists()).toBe(false);
  });
});
