import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import FormDialog from "./FormDialog.vue";

describe("FormDialog", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("renders an Ant Design modal and emits cancel on Escape", async () => {
    const wrapper = mount(FormDialog, {
      props: { visible: true, title: "新建" },
      attachTo: document.body,
    });
    expect(document.querySelector(".ant-modal")).not.toBeNull();
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("does not emit cancel on Escape when hidden", () => {
    const wrapper = mount(FormDialog, { props: { visible: false, title: "新建" } });
    expect(wrapper.find(".ant-modal").exists()).toBe(false);
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(wrapper.emitted("cancel")).toBeUndefined();
    wrapper.unmount();
  });

  it("emits cancel on mask click but not modal body click", async () => {
    const wrapper = mount(FormDialog, {
      props: { visible: true, title: "新建" },
      attachTo: document.body,
    });
    expect(document.querySelector(".ant-modal")).not.toBeNull();
    await wrapper.get(".ant-modal-wrap").trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    await wrapper.get(".ant-modal-body").trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("keeps cancel and submit buttons", async () => {
    const wrapper = mount(FormDialog, {
      props: { visible: true, title: "新建" },
      attachTo: document.body,
    });
    await wrapper.get('[data-testid="form-cancel"]').trigger("click");
    await wrapper.get('[data-testid="form-submit"]').trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    expect(wrapper.emitted("submit")).toHaveLength(1);
    wrapper.unmount();
  });

  it("shows write-fail feedback inside the open modal", () => {
    const wrapper = mount(FormDialog, {
      props: {
        visible: true,
        title: "新建",
        feedback: { message: "配置值不合法", traceId: "trace-cfg" },
      },
      attachTo: document.body,
    });
    expect(wrapper.get('[data-testid="form-dialog"]').text()).toContain("配置值不合法");
    expect(wrapper.get('[data-testid="page-error"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it("keeps the form body slot inside the modal", () => {
    const wrapper = mount(FormDialog, {
      props: { visible: true, title: "新建" },
      attachTo: document.body,
      slots: {
        default: `<div class="ant-form-item">字段一</div>`,
      },
    });
    expect(wrapper.get('[data-testid="form-dialog-body"]').text()).toContain("字段一");
    expect(wrapper.get('[data-testid="form-submit"]').exists()).toBe(true);
    expect(wrapper.get(".ant-modal-footer").exists()).toBe(true);
    wrapper.unmount();
  });
});
