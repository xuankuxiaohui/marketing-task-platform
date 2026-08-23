import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { visibleText } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("@/api/simulate", () => ({
  simulateList: vi.fn(),
  simulateDetail: vi.fn(),
  simulateStart: vi.fn(),
  simulateClick: vi.fn(),
  simulateCallback: vi.fn(),
  simulateProgress: vi.fn(),
  simulateFlow: vi.fn(),
  simulateReverse: vi.fn(),
}));

import {
  simulateFlow,
  simulateList,
  simulateReverse,
  simulateStart,
} from "@/api/simulate";
import SimulateTaskPage from "./index.vue";

const listMock = vi.mocked(simulateList);
const startMock = vi.mocked(simulateStart);
const flowMock = vi.mocked(simulateFlow);
const reverseMock = vi.mocked(simulateReverse);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(SimulateTaskPage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("SimulateTaskPage", () => {
  beforeEach(() => {
    listMock.mockReset();
    startMock.mockReset();
    flowMock.mockReset();
    reverseMock.mockReset();
    listMock.mockResolvedValue(
      ok({
        total: 1,
        records: [{ taskId: 3, taskCode: "daily", name: "每日任务", userStatus: "NONE" }],
      }),
    );
    startMock.mockResolvedValue(ok({ instanceId: 11, instanceStatus: "IN_PROGRESS" }));
    flowMock.mockResolvedValue(
      ok({
        instanceId: 11,
        instanceStatus: "COMPLETED",
        steps: [{ stepCode: "c1", type: "CLICK", action: "click", stepStatus: "COMPLETED" }],
        grantRecordIds: [501],
      }),
    );
    reverseMock.mockResolvedValue(
      ok({ instanceId: 11, pointsReversed: 1, stockRestored: 1, sendingMarked: 0, channelRevoked: false }),
    );
  });

  it("lists tasks then runs flow and reverse without channel revoke", async () => {
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="simulate-user"]').setValue("8");
    await wrapper.get('[data-testid="simulate-task"]').setValue("3");
    await wrapper.get('[data-testid="simulate-list"]').trigger("click");
    await flushPromises();
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ userId: 8 }));
    expect(wrapper.get('[data-testid="simulate-table"]').text()).toContain("daily");
    await wrapper.get('[data-testid="simulate-flow"]').trigger("click");
    await flushPromises();
    expect(flowMock).toHaveBeenCalledWith(8, 3);
    expect(wrapper.get('[data-testid="simulate-flow-table"]').text()).toContain("CLICK");
    await wrapper.get('[data-testid="simulate-reverse"]').trigger("click");
    await flushPromises();
    expect(reverseMock).toHaveBeenCalledWith(11);
    expect(wrapper.get('[data-testid="simulate-result"]').text()).toContain("channel=false");
    expect(visibleText(wrapper, "simulate-start")).toContain(zhCN.simulate.start);
  });
});
