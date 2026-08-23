import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";
import { CONFIG_MASK_DISPLAY } from "@/utils/config-update";

vi.mock("@/api/system", () => ({
  pageConfigs: vi.fn(),
  createConfig: vi.fn(),
  updateConfig: vi.fn(),
}));

import { pageConfigs, updateConfig } from "@/api/system";
import ConfigPage from "./index.vue";

const pageConfigsMock = vi.mocked(pageConfigs);
const updateConfigMock = vi.mocked(updateConfig);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(ConfigPage, {
    global: { plugins: [pinia], directives: { auth } },
    attachTo: document.body,
  });
  await flushPromises();
  return wrapper;
}

describe("ConfigManagePage", () => {
  beforeEach(() => {
    pageConfigsMock.mockReset();
    updateConfigMock.mockReset();
    pageConfigsMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 1,
            configKey: "auth.init-secret",
            configGroup: "auth",
            configValue: CONFIG_MASK_DISPLAY,
            valueType: "STRING",
            masked: true,
            status: "ENABLED",
            remark: "",
          },
        ],
      }),
    );
    updateConfigMock.mockResolvedValue(ok({ ok: true }));
  });

  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("omits value when a masked config is saved without a new secret", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="config-value"]').text()).toBe(CONFIG_MASK_DISPLAY);
    await wrapper.get('[data-testid="config-edit"]').trigger("click");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(updateConfigMock).toHaveBeenCalledTimes(1);
    const [key, body] = updateConfigMock.mock.calls[0];
    expect(key).toBe("auth.init-secret");
    expect(body).not.toHaveProperty("value");
    expect(body).toMatchObject({ configGroup: "auth", status: "ENABLED" });
    expect(document.body.textContent).toContain(zhCN.common.saved);
  });

  it("keeps the dialog open and shows the fail message plus traceId", async () => {
    updateConfigMock.mockResolvedValue(fail("common.param-invalid", "配置值不合法", "trace-cfg"));
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="config-edit"]').trigger("click");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(wrapper.find('[data-testid="form-dialog"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="form-dialog"]').text()).toContain("配置值不合法");
    expect(wrapper.get('[data-testid="form-dialog"]').text()).toContain("trace-cfg");
    expect(wrapper.get('[data-testid="form-dialog"]').find('[data-testid="copy-trace"]').exists()).toBe(true);
  });
});
