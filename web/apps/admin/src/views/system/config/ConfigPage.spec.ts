import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";
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

  it("omits value when a masked config is saved without a new secret", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(ConfigPage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    expect(wrapper.get('[data-testid="config-value"]').text()).toBe(CONFIG_MASK_DISPLAY);
    await wrapper.get('[data-testid="config-edit"]').trigger("click");
    await wrapper.get('[data-testid="form-dialog"] form').trigger("submit.prevent");
    await flushPromises();
    expect(updateConfigMock).toHaveBeenCalledTimes(1);
    const [key, body] = updateConfigMock.mock.calls[0];
    expect(key).toBe("auth.init-secret");
    expect(body).not.toHaveProperty("value");
    expect(body).toMatchObject({ configGroup: "auth", status: "ENABLED" });
  });
});
