import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { visibleText } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("@/api/ad", () => ({
  pageMaterials: vi.fn(),
  saveMaterial: vi.fn(),
  deleteMaterial: vi.fn(),
}));

import { pageMaterials } from "@/api/ad";
import AdMaterialPage from "./index.vue";

const pageMock = vi.mocked(pageMaterials);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(AdMaterialPage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("AdMaterialPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 2,
            title: "夏日",
            imageUrl: "https://cdn.example/a.png",
            jumpType: "NONE",
            weight: 10,
            startTime: "2026-08-01T00:00:00Z",
            endTime: "2026-08-31T00:00:00Z",
            status: "ENABLED",
          },
        ],
      }),
    );
  });

  it("lists materials", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="ad-material-table"]').text()).toContain("夏日");
    expect(visibleText(wrapper, "ad-material-create")).toContain(zhCN.common.create);
  });
});
