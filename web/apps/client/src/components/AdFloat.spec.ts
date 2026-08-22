import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/ad", () => ({
  fetchAdPosition: vi.fn(),
  dismissAdMaterial: vi.fn(),
}));

vi.mock("@/tracking", () => ({
  TRACK: { AD_FLOAT_EXPOSURE: "ad.float.exposure", AD_FLOAT_CLICK: "ad.float.click" },
  track: vi.fn(),
}));

import { dismissAdMaterial, fetchAdPosition } from "@/api/ad";
import AdFloat from "./AdFloat.vue";

const adMock = vi.mocked(fetchAdPosition);
const dismissMock = vi.mocked(dismissAdMaterial);

describe("AdFloat", () => {
  beforeEach(() => {
    adMock.mockReset();
    dismissMock.mockReset();
    dismissMock.mockResolvedValue(ok({ ok: true }));
  });

  it("closes and consumes remaining quota", async () => {
    adMock.mockResolvedValue(
      ok({
        code: "home_float",
        form: "FLOAT",
        materials: [
          {
            materialId: 8,
            trackId: "p:8:d",
            title: "float",
            imageUrl: "https://cdn.example/f.png",
            jumpType: "NONE",
            weight: 1,
          },
        ],
      }),
    );
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/", component: { template: "<div />" } }],
    });
    await router.push("/");
    await router.isReady();
    const wrapper = mount(AdFloat, { global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.find('[data-testid="ad-float"]').exists()).toBe(true);
    await wrapper.get('[data-testid="ad-float-close"]').trigger("click");
    await flushPromises();
    expect(dismissMock).toHaveBeenCalledWith(8, "home_float");
    expect(wrapper.find('[data-testid="ad-float"]').exists()).toBe(false);
  });

  it("renders nothing when the float slot is missing", async () => {
    adMock.mockResolvedValue(fail("ad.position.not-found", "广告位不存在"));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/", component: { template: "<div />" } }],
    });
    await router.push("/");
    await router.isReady();
    const wrapper = mount(AdFloat, { global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.find('[data-testid="ad-float"]').exists()).toBe(false);
  });
});
