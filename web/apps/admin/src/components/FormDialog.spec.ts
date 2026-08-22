import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import FormDialog from "./FormDialog.vue";

describe("FormDialog", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("emits cancel on Escape when visible", async () => {
    const wrapper = mount(FormDialog, { props: { visible: true, title: "新建" }, attachTo: document.body });
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("does not emit cancel on Escape when hidden", () => {
    const wrapper = mount(FormDialog, { props: { visible: false, title: "新建" } });
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    expect(wrapper.emitted("cancel")).toBeUndefined();
    wrapper.unmount();
  });

  it("emits cancel on mask click but not card click", async () => {
    const wrapper = mount(FormDialog, { props: { visible: true, title: "新建" } });
    await wrapper.get('[data-testid="form-dialog"]').trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    await wrapper.get(".form-card").trigger("click");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("keeps cancel and submit buttons", async () => {
    const wrapper = mount(FormDialog, { props: { visible: true, title: "新建" } });
    await wrapper.get('[data-testid="form-cancel"]').trigger("click");
    await wrapper.get("form.form-card").trigger("submit");
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    expect(wrapper.emitted("submit")).toHaveLength(1);
    expect(wrapper.get('[data-testid="form-submit"]').attributes("disabled")).toBeUndefined();
    wrapper.unmount();
  });
});
