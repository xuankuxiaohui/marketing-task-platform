import { flushPromises, type VueWrapper } from "@vue/test-utils";

export async function setControl(wrapper: VueWrapper, testid: string, value: unknown): Promise<void> {
  await wrapper.get(`[data-testid="${testid}"]`).setValue(value as string | number | boolean);
  await flushPromises();
}
