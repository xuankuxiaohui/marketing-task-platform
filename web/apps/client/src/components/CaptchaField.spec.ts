import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import CaptchaField from "./CaptchaField.vue";

describe("CaptchaField", () => {
  it("shows the captcha image and emits refresh on click (R32.2)", async () => {
    const wrapper = mount(CaptchaField, {
      props: { modelValue: "", image: "data:image/png;base64,xx" },
    });
    expect(wrapper.get('[data-testid="login-captcha-image"]').attributes("src")).toBe(
      "data:image/png;base64,xx",
    );
    await wrapper.get('[data-testid="login-captcha-refresh"]').trigger("click");
    expect(wrapper.emitted("refresh")).toHaveLength(1);
  });
});
