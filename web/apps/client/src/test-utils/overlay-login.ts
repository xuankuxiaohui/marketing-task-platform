import { flushPromises, type VueWrapper } from "@vue/test-utils";
import { setField } from "./form";

export async function submitOverlayLogin(wrapper: VueWrapper): Promise<void> {
  await setField(wrapper, "login-username", "bob_01");
  await setField(wrapper, "login-password", "abcdefg1");
  await setField(wrapper, "login-captcha", "ab12");
  await wrapper.get("form").trigger("submit.prevent");
  await flushPromises();
}
