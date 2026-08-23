import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { visibleText } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("@/api/activity", () => ({
  pageActivities: vi.fn(),
  saveActivity: vi.fn(),
  deleteActivity: vi.fn(),
  publishActivity: vi.fn(),
  scheduleActivity: vi.fn(),
  offlineActivity: vi.fn(),
}));

import { pageActivities, publishActivity } from "@/api/activity";
import ActivityManagePage from "./index.vue";

const pageMock = vi.mocked(pageActivities);
const publishMock = vi.mocked(publishActivity);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(ActivityManagePage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("ActivityManagePage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    publishMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 3,
            code: "summer",
            name: "夏季专题",
            startTime: "2026-08-01T00:00:00Z",
            endTime: "2026-08-31T16:00:00Z",
            status: "DRAFT",
            version: 0,
            pendingRevision: false,
            richText: "<p>ok</p>",
            newUserOnly: false,
            newUserDays: 7,
          },
        ],
      }),
    );
  });

  it("lists activities and previews publish confirm", async () => {
    publishMock.mockResolvedValue(
      ok({
        requiresConfirm: true,
        message: zhCN.activity.revisionHint,
        id: 3,
        code: "summer",
        version: 0,
        status: "DRAFT",
      }),
    );
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="activity-table"]').text()).toContain("summer");
    expect(wrapper.find(".ant-pagination").exists()).toBe(true);
    expect(wrapper.find(".pager").exists()).toBe(false);
    expect(visibleText(wrapper, "activity-create")).toContain(zhCN.common.create);
    await wrapper.get('[data-testid="activity-publish"]').trigger("click");
    await flushPromises();
    expect(publishMock).toHaveBeenCalledWith(3, { confirm: false, early: true });
  });

  it("shows Ant Design empty and date picker instead of homemade chrome", async () => {
    pageMock.mockResolvedValue(ok({ total: 0, records: [] }));
    const wrapper = await mountPage();
    expect(wrapper.find(".ant-empty").exists()).toBe(true);
    expect(wrapper.get('[data-testid="page-empty"]').exists()).toBe(true);
    expect(wrapper.find(".pager").exists()).toBe(false);
    await wrapper.get('[data-testid="activity-create"]').trigger("click");
    await flushPromises();
    expect(wrapper.find(".ant-picker").exists()).toBe(true);
    expect(wrapper.find('input[type="datetime-local"]').exists()).toBe(false);
  });
});
