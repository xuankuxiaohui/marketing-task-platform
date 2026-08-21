import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { setControl } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("@/api/track", () => ({
  debugEvents: vi.fn(),
}));

import { debugEvents } from "@/api/track";
import TrackEventPage from "./index.vue";

const debugMock = vi.mocked(debugEvents);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(TrackEventPage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("TrackEventPage", () => {
  beforeEach(() => {
    debugMock.mockReset();
    debugMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 100,
            source: "CLIENT",
            eventCode: "page.view",
            userId: 9,
            deviceId: "dev-9",
            registered: true,
            simulated: false,
            serverTime: "2026-08-20T00:00:00Z",
            events: [{ code: "page.view", props: { route: "/home" } }],
          },
        ],
      }),
    );
  });

  it("queries debug events without write controls", async () => {
    const wrapper = await mountPage();
    expect(debugMock).toHaveBeenCalledTimes(1);
    expect(debugMock).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 1,
        pageSize: 20,
      }),
    );
    expect(wrapper.get('[data-testid="debug-no-side-effect"]').text()).toBe(zhCN.trackEvent.hint);
    expect(wrapper.get('[data-testid="debug-table"]').text()).toContain("page.view");
    expect(wrapper.get('[data-testid="debug-events"]').text()).toContain("/home");
    expect(wrapper.find('[data-testid="debug-delete"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="debug-save"]').exists()).toBe(false);
    expect(wrapper.find(".row-actions").exists()).toBe(false);
  });

  it("sends filter query on GET only", async () => {
    const wrapper = await mountPage();
    await setControl(wrapper, "filter-code", "page.view");
    await setControl(wrapper, "filter-user", "9");
    await setControl(wrapper, "filter-source", "CLIENT");
    await setControl(wrapper, "filter-device", "dev-9");
    await wrapper.get('[data-testid="debug-query"]').trigger("click");
    await flushPromises();
    expect(debugMock).toHaveBeenLastCalledWith({
      eventCode: "page.view",
      userId: 9,
      source: "CLIENT",
      deviceId: "dev-9",
      from: undefined,
      to: undefined,
      page: 1,
      pageSize: 20,
    });
    expect(debugMock).toHaveBeenCalledTimes(2);
  });
});
