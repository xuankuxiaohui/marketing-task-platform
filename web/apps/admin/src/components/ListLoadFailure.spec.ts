import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import ListLoadFailure from "./ListLoadFailure.vue";

describe("ListLoadFailure", () => {
  it("renders locked copy and emits reload", async () => {
    const wrapper = mount(ListLoadFailure);
    expect(wrapper.get('[data-testid="list-load-failed-title"]').text()).toBe(zhCN.common.listLoadFailed);
    expect(wrapper.get('[data-testid="list-load-failed-hint"]').text()).toBe(zhCN.common.listLoadFailedHint);
    expect(wrapper.get('[data-testid="list-load-reload"]').text()).toBe(zhCN.common.reload);
    await wrapper.get('[data-testid="list-load-reload"]').trigger("click");
    expect(wrapper.emitted("reload")).toHaveLength(1);
  });
});
