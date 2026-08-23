import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import ConfirmDialog from "./ConfirmDialog.vue";

describe("ConfirmDialog", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("renders an Ant Design modal and emits cancel on Escape", async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { visible: true, message: "确认删除？" },
      attachTo: document.body,
    });
    expect(document.querySelector(".ant-modal")).not.toBeNull();
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("does not emit cancel on Escape when hidden", () => {
    const wrapper = mount(ConfirmDialog, { props: { visible: false, message: "确认删除？" } });
    expect(wrapper.find(".ant-modal").exists()).toBe(false);
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(wrapper.emitted("cancel")).toBeUndefined();
    wrapper.unmount();
  });

  it("emits cancel on mask click but not modal body click", async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { visible: true, message: "确认删除？" },
      attachTo: document.body,
    });
    expect(document.querySelector(".ant-modal")).not.toBeNull();
    await wrapper.get(".ant-modal-wrap").trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    await wrapper.get(".ant-modal-body").trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("keeps cancel and confirm buttons", async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { visible: true, message: "确认删除？" },
      attachTo: document.body,
    });
    await wrapper.get('[data-testid="confirm-cancel"]').trigger("click");
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    expect(wrapper.emitted("confirm")).toHaveLength(1);
    wrapper.unmount();
  });
});
