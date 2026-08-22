import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Result } from "@mkt/shared";
import type { AdminLoginData, CaptchaData } from "@/api/auth";

vi.mock("@/api/auth", () => ({
  CAPTCHA_ERROR_CODES: new Set(["auth.captcha.invalid", "auth.captcha.expired"]),
  fetchCaptcha: vi.fn(),
  login: vi.fn(),
}));

vi.mock("@/router/session", () => ({
  ensureDynamicRoutes: vi.fn().mockResolvedValue(true),
}));

import { fetchCaptcha, login } from "@/api/auth";
import LoginPage from "./index.vue";

const fetchCaptchaMock = vi.mocked(fetchCaptcha);
const loginMock = vi.mocked(login);

function ok<T>(data: T): Result<T> {
  return { code: 0, message: "ok", data };
}

function fail(code: string, message: string): Result {
  return { code, message };
}

async function mountLogin(query: Record<string, string> = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/login", component: LoginPage },
      { path: "/dashboard", component: { template: "<div />" } },
      { path: "/change-password", component: { template: "<div />" } },
      { path: "/system/users", component: { template: "<div />" } },
    ],
  });
  await router.push({ path: "/login", query });
  await router.isReady();
  const wrapper = mount(LoginPage, {
    global: {
      plugins: [createPinia(), router],
    },
  });
  await flushPromises();
  return { wrapper, router };
}


async function setTestidInput(wrapper: VueWrapper, testid: string, value: string): Promise<void> {
  const root = wrapper.get(`[data-testid="${testid}"]`);
  const inner = root.find("input");
  if (inner.exists()) {
    await inner.setValue(value);
    return;
  }
  await root.setValue(value);
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

  it("loads captcha on mount", async () => {
    const { wrapper } = await mountLogin();
    expect(fetchCaptchaMock).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-testid="login-captcha-image"]').attributes("src")).toBe(
      "data:image/png;base64,xx",
    );
  });

  it("submits credentials and goes to dashboard", async () => {
    loginMock.mockResolvedValue(
      ok<AdminLoginData>({
        userId: 1,
        nickname: "超管",
        roles: ["super-admin"],
        permissions: ["identity:admin-user:query"],
        mustChangePassword: false,
        csrfToken: "csrf-1",
      }),
    );
    const { wrapper, router } = await mountLogin();
    await setTestidInput(wrapper, "login-username", "admin");
    await setTestidInput(wrapper, "login-password", "Admin123!x");
    await setTestidInput(wrapper, "login-captcha", "ab12");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(loginMock).toHaveBeenCalledWith({
      username: "admin",
      password: "Admin123!x",
      captchaId: "cid-1",
      captchaCode: "ab12",
    });
    expect(router.currentRoute.value.path).toBe("/dashboard");
  });

  it("sends first-login admin to change-password", async () => {
    loginMock.mockResolvedValue(
      ok<AdminLoginData>({
        userId: 1,
        nickname: "超管",
        roles: ["super-admin"],
        permissions: ["identity:admin-user:query"],
        mustChangePassword: true,
        csrfToken: "csrf-1",
      }),
    );
    const { wrapper, router } = await mountLogin();
    await setTestidInput(wrapper, "login-username", "admin");
    await setTestidInput(wrapper, "login-password", "Admin123!x");
    await setTestidInput(wrapper, "login-captcha", "ab12");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/change-password");
  });

  it("refreshes captcha when captcha is invalid", async () => {
    loginMock.mockResolvedValue(fail("auth.captcha.invalid", "验证码错误") as Result<AdminLoginData>);
    fetchCaptchaMock
      .mockResolvedValueOnce(ok<CaptchaData>({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }))
      .mockResolvedValueOnce(ok<CaptchaData>({ captchaId: "cid-2", imageBase64: "data:image/png;base64,yy" }));
    const { wrapper } = await mountLogin();
    await setTestidInput(wrapper, "login-username", "admin");
    await setTestidInput(wrapper, "login-password", "Admin123!x");
    await setTestidInput(wrapper, "login-captcha", "bad");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(wrapper.get('[data-testid="login-error"]').text()).toBe("验证码错误");
    expect(fetchCaptchaMock).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="login-captcha-image"]').attributes("src")).toBe(
      "data:image/png;base64,yy",
    );
  });

  it("shows unified credential error without refreshing captcha", async () => {
    loginMock.mockResolvedValue(
      fail("auth.login.invalid-credential", "用户名或密码错误") as Result<AdminLoginData>,
    );
    const { wrapper } = await mountLogin();
    await setTestidInput(wrapper, "login-username", "admin");
    await setTestidInput(wrapper, "login-password", "wrong");
    await setTestidInput(wrapper, "login-captcha", "ab12");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(wrapper.get('[data-testid="login-error"]').text()).toBe("用户名或密码错误");
    expect(fetchCaptchaMock).toHaveBeenCalledTimes(1);
  });
});
