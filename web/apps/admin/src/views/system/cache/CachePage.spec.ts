import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";
import { SESSION_NAMESPACE } from "@/utils/cache-evict";

vi.mock("@/api/system", () => ({
  fetchCacheStats: vi.fn(),
  evictCache: vi.fn(),
}));

import { evictCache, fetchCacheStats } from "@/api/system";
import CachePage from "./index.vue";

const statsMock = vi.mocked(fetchCacheStats);
const evictMock = vi.mocked(evictCache);

describe("CacheManagePage", () => {
  beforeEach(() => {
    statsMock.mockReset();
    evictMock.mockReset();
    statsMock.mockResolvedValue(
      ok({
        total: 2,
        records: [
          { namespace: SESSION_NAMESPACE, keyCount: "N/A", hitRate: "N/A" },
          { namespace: "dict", keyCount: "3", hitRate: "0.8" },
        ],
      }),
    );
    evictMock.mockResolvedValue(ok({ evictedRedis: 1, notifiedInstances: 1 }));
  });

  it("blocks identity:session eviction without calling the API", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(CachePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    await wrapper.get('[data-testid="evict-namespace"]').setValue(SESSION_NAMESPACE);
    await wrapper.get('[data-testid="cache-evict-form"]').trigger("submit.prevent");
    await flushPromises();
    expect(evictMock).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="page-error"]').text()).toContain(zhCN.cache.sessionForbidden);
  });

  it("evicts a non-session namespace", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    useSessionStore().permissions = Object.values(PERMS);
    const wrapper = mount(CachePage, { global: { plugins: [pinia], directives: { auth } } });
    await flushPromises();
    await wrapper.get('[data-testid="evict-namespace"]').setValue("dict");
    await wrapper.get('[data-testid="cache-evict-form"]').trigger("submit.prevent");
    await flushPromises();
    expect(evictMock).toHaveBeenCalledWith({ level: "NAMESPACE", namespace: "dict" });
  });
});
