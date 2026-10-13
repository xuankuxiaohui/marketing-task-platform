import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { fail, ok } from "@/test-utils/result";
import { zhCN } from "@/locales/zh-CN";

vi.mock("@/api/reward", () => ({
  pagePrizes: vi.fn(),
  createPrize: vi.fn(),
  updatePrize: vi.fn(),
  deletePrize: vi.fn(),
  disablePrize: vi.fn(),
  enablePrize: vi.fn(),
  replenishStock: vi.fn(),
}));

import { disablePrize, pagePrizes } from "@/api/reward";
import PrizePage from "./index.vue";

const pageMock = vi.mocked(pagePrizes);
const disableMock = vi.mocked(disablePrize);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(PrizePage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("RewardPrizePage disable confirm", () => {
  beforeEach(() => {
    pageMock.mockReset();
    disableMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 9,
            code: "coupon_a",
            name: "券",
            categoryCode: "COUPON",
            status: "ENABLED",
            remainingStock: 10,
            totalStock: 10,
          },
        ],
      }),
    );
  });

  it("loads impact with confirm=false then disables with confirm=true", async () => {
    disableMock.mockImplementation((_id: number, body: { confirm: boolean }) =>
      Promise.resolve(
        ok({
          confirmed: body.confirm,
          affectedTaskCount: 2,
          inFlightInstanceCount: 4,
        }),
      ),
    );
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="prize-disable"]').trigger("click");
    await flushPromises();
    expect(disableMock).toHaveBeenCalledWith(9, { confirm: false });
    expect(wrapper.get('[data-testid="confirm-message"]').text()).toContain("受影响任务 2 个，在途实例 4 个");
    expect(disableMock).toHaveBeenCalledTimes(1);
    await wrapper.get('[data-testid="confirm-cancel"]').trigger("click");
    expect(disableMock).toHaveBeenCalledTimes(1);
    await wrapper.get('[data-testid="prize-disable"]').trigger("click");
    await flushPromises();
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    await flushPromises();
    expect(disableMock).toHaveBeenLastCalledWith(9, { confirm: true });
  });
});


describe("RewardPrizePage list failure state", () => {
  beforeEach(() => {
    pageMock.mockReset();
    disableMock.mockReset();
  });

  it("shows locked failure copy and hides empty state without leaking codes", async () => {
    pageMock.mockResolvedValue(fail("reward.prize.unknown-code", "raw server text"));
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="list-load-failed-title"]').text()).toBe(zhCN.common.listLoadFailed);
    expect(wrapper.get('[data-testid="list-load-failed-hint"]').text()).toBe(zhCN.common.listLoadFailedHint);
    expect(wrapper.get('[data-testid="list-load-reload"]').text()).toBe(zhCN.common.reload);
    expect(wrapper.find('[data-testid="page-empty"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain("reward.prize.unknown-code");
    expect(wrapper.text()).not.toContain("raw server text");
    expect(wrapper.find('[data-testid="page-error"]').exists()).toBe(false);
  });

  it("retries from the failure state and restores the success path", async () => {
    pageMock
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce(
        ok({
          total: 1,
          records: [
            {
              id: 9,
              code: "coupon_a",
              name: "券",
              categoryCode: "COUPON",
              status: "ENABLED",
              remainingStock: 10,
              totalStock: 10,
            },
          ],
        }),
      );
    const wrapper = await mountPage();
    expect(wrapper.find('[data-testid="list-load-failure"]').exists()).toBe(true);
    await wrapper.get('[data-testid="list-load-reload"]').trigger("click");
    await flushPromises();
    expect(wrapper.find('[data-testid="list-load-failure"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="prize-table"]').text()).toContain("coupon_a");
  });

  it("keeps empty state on successful empty list", async () => {
    pageMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const wrapper = await mountPage();
    expect(wrapper.find('[data-testid="list-load-failure"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="page-empty"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="page-empty"]').text()).toContain(zhCN.common.empty);
  });
});
