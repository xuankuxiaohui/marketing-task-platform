import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CaptchaData, PortalAuthData } from "@/api/auth";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { setField } from "@/test-utils/form";
import { ok } from "@/test-utils/result";

vi.mock("@/api/auth", async () => {
  const actual = await vi.importActual<typeof import("@/api/auth")>("@/api/auth");
  return {
    ...actual,
    fetchCaptcha: vi.fn(),
    login: vi.fn(),
  };
});

import { fetchCaptcha, login } from "@/api/auth";
import LoginOverlay from "./LoginOverlay.vue";

const fetchCaptchaMock = vi.mocked(fetchCaptcha);
const loginMock = vi.mocked(login);

async function mountOverlay(path = "/home") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/home", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
      { path: "/register", component: { template: "<div />" } },
      { path: "/mine/password", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const pinia = createPinia();
  setActivePinia(pinia);
  const overlay = useLoginOverlayStore();
  overlay.request({ redirect: path });
  const wrapper = mount(LoginOverlay, { global: { plugins: [pinia, router] } });
  await flushPromises();
  return { wrapper, router, overlay };
}

describe("LoginOverlay", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    fetchCaptchaMock.mockReset();
    loginMock.mockReset();
    fetchCaptchaMock.mockResolvedValue(
      ok<CaptchaData>({ captchaId: "cid-1", imageBase64: "data:image/png;base64,xx" }),
    );
  });

  it("opens as a half-screen sheet on the host route", async () => {
    const { wrapper, router } = await mountOverlay("/home");
    const sheet = wrapper.get('[data-testid="login-overlay"]');
    expect(sheet.attributes("style") ?? "").toMatch(/62vh/);
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("dismisses without navigating to /login", async () => {
    const { wrapper, router, overlay } = await mountOverlay("/home");
    overlay.close();
    await flushPromises();
    expect(wrapper.find('[data-testid="login-overlay"]').exists()).toBe(true);
    expect(overlay.visible).toBe(false);
    expect(router.currentRoute.value.path).toBe("/home");
  });

  it("stays on the host route after a successful login and runs resume", async () => {
    const resume = vi.fn();
    loginMock.mockResolvedValue(ok<PortalAuthData>({ token: "client:t", userId: 9, nickname: "用户9" }));
    const { wrapper, router, overlay } = await mountOverlay("/home");
    overlay.request({ redirect: "/home", resume });
    await setField(wrapper, "login-username", "bob_01");
    await setField(wrapper, "login-password", "abcdefg1");
    await setField(wrapper, "login-captcha", "ab12");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    expect(loginMock).toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/home");
    expect(overlay.visible).toBe(false);
    expect(resume).toHaveBeenCalled();
  });
});
