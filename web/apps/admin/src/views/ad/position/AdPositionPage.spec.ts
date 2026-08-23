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
  pagePositions: vi.fn(),
  getPosition: vi.fn(),
  savePosition: vi.fn(),
  deletePosition: vi.fn(),
  bindPlacement: vi.fn(),
  unbindPlacement: vi.fn(),
}));

import { pagePositions } from "@/api/ad";
import AdPositionPage from "./index.vue";

const pageMock = vi.mocked(pagePositions);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(AdPositionPage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("AdPositionPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 1,
            code: "home_banner",
            name: "首页轮播",
            form: "CAROUSEL",
            platforms: ["WEB"],
            status: "ENABLED",
            placements: [],
            overlapCount: 0,
          },
        ],
      }),
    );
  });

  it("lists positions", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="ad-position-table"]').text()).toContain("home_banner");
    expect(visibleText(wrapper, "ad-position-create")).toContain(zhCN.common.create);
  });
});
