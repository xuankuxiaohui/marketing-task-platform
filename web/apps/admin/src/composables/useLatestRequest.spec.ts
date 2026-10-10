import { defineComponent, ref } from "vue";
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { useLatestRequest } from "./useLatestRequest";

describe("useLatestRequest", () => {
  it("invalidates prior guards when a newer request begins", () => {
    let begin!: () => () => boolean;
    const wrapper = mount(
      defineComponent({
        setup() {
          begin = useLatestRequest(() => null);
          return () => null;
        },
      }),
    );
    const first = begin();
    const second = begin();
    expect(first()).toBe(false);
    expect(second()).toBe(true);
    wrapper.unmount();
  });

  it("invalidates guards when the scope changes", () => {
    const scope = ref(1);
    let begin!: () => () => boolean;
    const wrapper = mount(
      defineComponent({
        setup() {
          begin = useLatestRequest(() => scope.value);
          return () => null;
        },
      }),
    );
    const guard = begin();
    expect(guard()).toBe(true);
    scope.value = 2;
    expect(guard()).toBe(false);
    wrapper.unmount();
  });

  it("invalidates guards on scope dispose", () => {
    let begin!: () => () => boolean;
    const wrapper = mount(
      defineComponent({
        setup() {
          begin = useLatestRequest(() => null);
          return () => null;
        },
      }),
    );
    const guard = begin();
    wrapper.unmount();
    expect(guard()).toBe(false);
  });
});
