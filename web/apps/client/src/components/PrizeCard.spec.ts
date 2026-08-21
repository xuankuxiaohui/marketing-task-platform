import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PrizeCardView } from "@/api/prize";
import { zhCN } from "@/locales/zh-CN";
import PrizeCard from "./PrizeCard.vue";

function prize(overrides: Partial<PrizeCardView> = {}): PrizeCardView {
  return {
    recordId: 11,
    prizeName: "积分礼包",
    prizeImage: "https://cdn.example/p.png",
    status: "WON",
    fulfillmentStatus: "NONE",
    expireAt: "2026-08-20T00:01:30.000Z",
    ...overrides,
  };
}

describe("PrizeCard", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows claimable countdown for WON (R35.1)", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-20T00:00:00.000Z"));
    const wrapper = mount(PrizeCard, { props: { prize: prize() } });
    expect(wrapper.get('[data-testid="prize-countdown"]').text()).toContain(zhCN.prize.remain);
    expect(wrapper.get('[data-testid="prize-countdown"]').text()).toContain("00:01:30");
    expect(wrapper.get('[data-testid="prize-action-11"]').text()).toBe(zhCN.prize.claim);
    expect((wrapper.get('[data-testid="prize-action-11"]').element as HTMLButtonElement).disabled).toBe(
      false,
    );
  });

  it("maps GRANTED + ARRIVED to a disabled arrived label (R35.2)", () => {
    const wrapper = mount(PrizeCard, {
      props: { prize: prize({ status: "GRANTED", fulfillmentStatus: "ARRIVED", expireAt: undefined }) },
    });
    expect(wrapper.get('[data-testid="prize-action-11"]').text()).toBe(zhCN.prize.arrived);
    expect((wrapper.get('[data-testid="prize-action-11"]').element as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(wrapper.find('[data-testid="prize-countdown"]').exists()).toBe(false);
  });

  it("emits claim when the action button is clicked", async () => {
    const wrapper = mount(PrizeCard, { props: { prize: prize({ expireAt: undefined }) } });
    await wrapper.get('[data-testid="prize-action-11"]').trigger("click");
    expect(wrapper.emitted("claim")).toHaveLength(1);
  });
});
