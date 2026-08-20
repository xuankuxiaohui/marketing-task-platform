import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CaptchaData, PortalAuthData, UsernameAvailableData } from "@/api/auth";
import { zhCN } from "@/locales/zh-CN";
import { setField } from "@/test-utils/form";
import { ok } from "@/test-utils/result";

vi.mock("@/api/auth", async () => {
  const actual = await vi.importActual<typeof import("@/api/auth")>("@/api/auth");
  return {
    ...actual,
    fetchCaptcha: vi.fn(),
    register: vi.fn(),
    usernameAvailable: vi.fn(),
  };
});

import { fetchCaptcha, register, usernameAvailable } from "@/api/auth";
import RegisterPage from "./index.vue";

const fetchCaptchaMock = vi.mocked(fetchCaptcha);
const registerMock = vi.mocked(register);
const availableMock = vi.mocked(usernameAvailable);

async function mountRegister() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/register", component: RegisterPage },
      { path: "/home", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/register");
  await router.isReady();
  const wrapper = mount(RegisterPage, {
    global: { plugins: [createPinia(), router] },
  });
  await flushPromises();
  return { wrapper, router };
}

describe("RegisterPage", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    fetchCaptchaMock.mockReset();
    registerMock.mockReset();
    availableMock.mockReset();
    fetchCaptchaMock.mockResolvedValue(
      ok<CaptchaData>({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }),
    );
    availableMock.mockResolvedValue(ok<UsernameAvailableData>({ available: true }));
  });

  it("keeps submit disabled until agreement is checked", async () => {
    const { wrapper } = await mountRegister();
    expect(wrapper.get('[data-testid="register-submit"]').attributes("disabled")).toBeDefined();
    await wrapper.get('[data-testid="register-agree"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="register-submit"]').attributes("disabled")).toBeUndefined();
  });

  it("shows password strength and username availability", async () => {
    const { wrapper } = await mountRegister();
    await setField(wrapper, "register-password", "short");
    expect(wrapper.get('[data-testid="register-password-hint"]').text()).toBe(zhCN.register.passwordHint);
    await setField(wrapper, "register-password", "abcdefg1");
    expect(wrapper.get('[data-testid="register-password-hint"]').text()).toBe(zhCN.register.passwordOk);
    await setField(wrapper, "register-username", "bob_01");
    await wrapper.get('[data-testid="register-username"]').find("input").trigger("blur");
    await flushPromises();
    expect(availableMock).toHaveBeenCalledWith("bob_01");
    expect(wrapper.get('[data-testid="register-username-hint"]').text()).toBe(zhCN.register.usernameAvailable);
  });

  it("registers, auto-logs in, and goes home", async () => {
    registerMock.mockResolvedValue(ok<PortalAuthData>({ token: "client:t", userId: 9, nickname: "用户9" }));
    const { wrapper, router } = await mountRegister();
    await setField(wrapper, "register-username", "bob_01");
    await setField(wrapper, "register-password", "abcdefg1");
    await setField(wrapper, "login-captcha", "ab12");
    await wrapper.get('[data-testid="register-agree"]').trigger("click");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(registerMock).toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("blocks submit without agreement even if the button is forced", async () => {
    const { wrapper } = await mountRegister();
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(registerMock).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="register-error"]').text()).toBe(zhCN.register.agreementRequired);
  });

  it("shows duplicate username from availability api", async () => {
    availableMock.mockResolvedValue(ok<UsernameAvailableData>({ available: false, reason: "duplicate" }));
    const { wrapper } = await mountRegister();
    await setField(wrapper, "register-username", "bob_01");
    await wrapper.get('[data-testid="register-username"]').find("input").trigger("blur");
    await flushPromises();
    expect(wrapper.get('[data-testid="register-username-hint"]').text()).toBe(zhCN.register.usernameDuplicate);
  });
});
