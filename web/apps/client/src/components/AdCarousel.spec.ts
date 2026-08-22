import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fail, ok } from "@/test-utils/result";

vi.mock("@/api/ad", () => ({
  fetchAdPosition: vi.fn(),
  dismissAdMaterial: vi.fn(),
}));

vi.mock("@/tracking", () => ({
  TRACK: { AD_CAROUSEL_EXPOSURE: "ad.carousel.exposure", AD_CAROUSEL_CLICK: "ad.carousel.click" },
  track: vi.fn(),
}));

vi.mock("vant", async () => {
  const actual = await vi.importActual<typeof import("vant")>("vant");
  return {
    ...actual,
    showFailToast: vi.fn(),
    showToast: vi.fn(),
  };
});

import { fetchAdPosition } from "@/api/ad";
import { showFailToast } from "vant";
import AdCarousel from "./AdCarousel.vue";

const adMock = vi.mocked(fetchAdPosition);

describe("AdCarousel", () => {
  beforeEach(() => {
    adMock.mockReset();
    vi.mocked(showFailToast).mockReset();
  });

  it("renders slides from the position payload", async () => {
    adMock.mockResolvedValue(
      ok({
        code: "home_banner",
        form: "CAROUSEL",
        materials: [
          {
            materialId: 1,
            trackId: "1:1:20260820",
            title: "banner",
            imageUrl: "https://cdn.example/a.png",
            jumpType: "NONE",
            weight: 10,
          },
        ],
        carouselIntervalSeconds: 5,
      }),
    );
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/", component: { template: "<div />" } }],
    });
    await router.push("/");
    await router.isReady();
    const wrapper = mount(AdCarousel, {
      props: { positionCode: "home_banner" },
      global: { plugins: [router] },
    });
    await flushPromises();
    expect(wrapper.find('[data-testid="ad-carousel"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="ad-carousel-slide"]').exists()).toBe(true);
  });

  it("renders nothing and does not toast when the position is missing", async () => {
    adMock.mockResolvedValue(fail("ad.position.not-found", "广告位不存在"));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/", component: { template: "<div />" } }],
    });
    await router.push("/");
    await router.isReady();
    const wrapper = mount(AdCarousel, {
      props: { positionCode: "home_banner" },
      global: { plugins: [router] },
    });
    await flushPromises();
    expect(wrapper.find('[data-testid="ad-carousel"]').exists()).toBe(false);
    expect(vi.mocked(showFailToast)).not.toHaveBeenCalled();
  });
});
