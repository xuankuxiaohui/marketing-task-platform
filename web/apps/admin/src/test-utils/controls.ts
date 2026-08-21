import { flushPromises, type VueWrapper } from "@vue/test-utils";

export async function setControl(wrapper: VueWrapper, testid: string, value: unknown): Promise<void> {
  const selector = `[data-testid="${testid}"]`;
  const root = wrapper.get(selector);
  const tag = root.element.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") {
    await root.setValue(value as string | number | boolean);
    return;
  }
  const comp = wrapper.findComponent(selector);
  if (comp.exists()) {
    await comp.setValue(value);
    await flushPromises();
    return;
  }
  const native = root.find("input, textarea, select");
  if (native.exists()) {
    await native.setValue(value as string | number | boolean);
    return;
  }
  await root.setValue(value as string | number | boolean);
}
