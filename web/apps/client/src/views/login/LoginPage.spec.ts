import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PortalAuthData, CaptchaData } from "@/api/auth";
import { setField } from "@/test-utils/form";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/auth", async () => {
  const actual = await vi.importActual<typeof import("@/api/auth")>("@/api/auth");
  return {
    ...actual,
    fetchCaptcha: vi.fn(),
    login: vi.fn(),
  };
});

import { fetchCaptcha, login } from "@/api/auth";
import LoginPage from "./index.vue";

const fetchCaptchaMock = vi.mocked(fetchCaptcha);
const loginMock = vi.mocked(login);

async function mountLogin(query: Record<string, string> = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/login", component: LoginPage },
      { path: "/home", component: { template: "<div />" } },
      { path: "/mine", component: { template: "<div />" } },
      { path: "/register", component: { template: "<div />" } },
    ],
  });
  await router.push({ path: "/login", query });
  await router.isReady();
  const wrapper = mount(LoginPage, {
    global: { plugins: [createPinia(), router] },
  });
  await flushPromises();
  return { wrapper, router };
}

describe("LoginPage", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    fetchCaptchaMock.mockReset();
    loginMock.mockReset();
    fetchCaptchaMock.mockResolvedValue(
      ok<CaptchaData>({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }),
    );
  });

  it("loads captcha on mount and refreshes on click", async () => {
    fetchCaptchaMock
      .mockResolvedValueOnce(ok<CaptchaData>({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }))
      .mockResolvedValueOnce(ok<CaptchaData>({ captchaId: "cid-2", imageBase64: "data:image/png;base64,yy" }));
    const { wrapper } = await mountLogin();
    expect(wrapper.get('[data-testid="login-captcha-image"]').attributes("src")).toBe(
      "data:image/png;base64,xx",
    );
    await wrapper.get('[data-testid="login-captcha-refresh"]').trigger("click");
    await flushPromises();
    expect(fetchCaptchaMock).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="login-captcha-image"]').attributes("src")).toBe(
      "data:image/png;base64,yy",
    );
  });

  it("submits credentials and goes to home", async () => {
    loginMock.mockResolvedValue(
      ok<PortalAuthData>({ token: "client:t", userId: 9, nickname: "用户9" }),
    );
    const { wrapper, router } = await mountLogin();
    await setField(wrapper, "login-username", "bob_01");
    await setField(wrapper, "login-password", "abcdefg1");
    await setField(wrapper, "login-captcha", "ab12");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(loginMock).toHaveBeenCalledWith({
      username: "bob_01",
      password: "abcdefg1",
      captchaId: "cid-1",
      captchaCode: "ab12",
    });
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("refreshes captcha when captcha is invalid", async () => {
    loginMock.mockResolvedValue(fail("auth.captcha.invalid", "验证码错误") as never);
    fetchCaptchaMock
      .mockResolvedValueOnce(ok<CaptchaData>({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }))
      .mockResolvedValueOnce(ok<CaptchaData>({ captchaId: "cid-2", imageBase64: "data:image/png;base64,yy" }));
    const { wrapper } = await mountLogin();
    await setField(wrapper, "login-username", "bob_01");
    await setField(wrapper, "login-password", "abcdefg1");
    await setField(wrapper, "login-captcha", "bad");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(wrapper.get('[data-testid="login-error"]').text()).toBe("验证码错误");
    expect(fetchCaptchaMock).toHaveBeenCalledTimes(2);
  });
});
