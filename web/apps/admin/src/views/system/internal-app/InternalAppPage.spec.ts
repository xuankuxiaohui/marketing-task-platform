import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

vi.mock("@/api/identity", () => ({
  pageInternalApps: vi.fn(),
  createInternalApp: vi.fn(),
  rotateInternalAppSecret: vi.fn(),
  disableInternalApp: vi.fn(),
  enableInternalApp: vi.fn(),
}));

import { createInternalApp, disableInternalApp, pageInternalApps } from "@/api/identity";
import InternalAppPage from "./index.vue";

const pageMock = vi.mocked(pageInternalApps);
const createMock = vi.mocked(createInternalApp);
const disableMock = vi.mocked(disableInternalApp);

describe("InternalAppPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    createMock.mockReset();
    disableMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [{ id: 3, appId: "app_1", appName: "回调方", status: "ENABLED" }],
      }),
    );
    createMock.mockResolvedValue(ok({ id: 4, appId: "app_2", secret: "ONCESECRET123" }));
    disableMock.mockResolvedValue(ok({ ok: true }));
  });

  it("shows secret only after create and confirms disable", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(InternalAppPage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    expect(wrapper.find('[data-testid="secret-once"]').exists()).toBe(false);
    await wrapper.get('[data-testid="app-create"]').trigger("click");
    await wrapper.get('[data-testid="app-name"]').setValue("新调用方");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(createMock).toHaveBeenCalledWith({ appName: "新调用方" });
    expect(wrapper.get('[data-testid="secret-value"]').text()).toBe("ONCESECRET123");
    await wrapper.get('[data-testid="app-disable"]').trigger("click");
    expect(wrapper.get('[data-testid="confirm-message"]').text()).toBe(zhCN.confirm.disable);
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    await flushPromises();
    expect(disableMock).toHaveBeenCalledWith(3);
  });
});
