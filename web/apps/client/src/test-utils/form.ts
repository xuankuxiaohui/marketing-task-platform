import type { VueWrapper } from "@vue/test-utils";

export async function setField(
  wrapper: VueWrapper,
  testId: string,
  value: string,
): Promise<void> {
  const root = wrapper.get(`[data-testid="${testId}"]`);
  const input = root.find("input");
  if (input.exists()) {
    await input.setValue(value);
    return;
  }
  await root.setValue(value);
}
