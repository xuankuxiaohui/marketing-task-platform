import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { useSessionStore } from "@/store/session";
import { ok } from "@/test-utils/result";

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
