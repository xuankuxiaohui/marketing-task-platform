import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import ConfirmDialog from "./ConfirmDialog.vue";

describe("ConfirmDialog", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("emits cancel on Escape when visible", async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { visible: true, message: "确认删除？" },
      attachTo: document.body,
    });
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("does not emit cancel on Escape when hidden", () => {
    const wrapper = mount(ConfirmDialog, { props: { visible: false, message: "确认删除？" } });
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    expect(wrapper.emitted("cancel")).toBeUndefined();
    wrapper.unmount();
  });

  it("emits cancel on mask click but not card click", async () => {
    const wrapper = mount(ConfirmDialog, { props: { visible: true, message: "确认删除？" } });
    await wrapper.get('[data-testid="confirm-dialog"]').trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    await wrapper.get(".confirm-card").trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("keeps cancel and confirm buttons", async () => {
    const wrapper = mount(ConfirmDialog, { props: { visible: true, message: "确认删除？" } });
    await wrapper.get('[data-testid="confirm-cancel"]').trigger("click");
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    expect(wrapper.emitted("confirm")).toHaveLength(1);
    wrapper.unmount();
  });
});
