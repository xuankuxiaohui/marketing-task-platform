import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FallbackImage from "./FallbackImage.vue";

describe("FallbackImage", () => {
  it("shows a placeholder when the remote image is missing or fails", async () => {
    const empty = mount(FallbackImage, { props: { alt: "任务图标" } });
    expect(empty.find('[data-testid="image-placeholder"]').exists()).toBe(true);

    const wrapper = mount(FallbackImage, { props: { src: "https://cdn.example/icon.png", alt: "任务图标" } });
    expect(wrapper.find('[data-testid="fallback-image"]').exists()).toBe(true);
    await wrapper.get('[data-testid="fallback-image"]').trigger("error");
    expect(wrapper.find('[data-testid="fallback-image"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="image-placeholder"]').exists()).toBe(true);
  });
});
