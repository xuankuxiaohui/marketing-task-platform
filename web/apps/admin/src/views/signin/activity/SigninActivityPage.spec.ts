import { message } from "ant-design-vue";
import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { auth } from "@/directives/auth";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { visibleText } from "@/test-utils/controls";
import { ok } from "@/test-utils/result";

vi.mock("@/api/signin", () => ({
  pageSigninActivities: vi.fn(),
  saveSigninActivity: vi.fn(),
  deleteSigninActivity: vi.fn(),
  publishSigninActivity: vi.fn(),
  scheduleSigninActivity: vi.fn(),
  offlineSigninActivity: vi.fn(),
}));

import { pageSigninActivities, publishSigninActivity } from "@/api/signin";
import SigninActivityPage from "./index.vue";

const pageMock = vi.mocked(pageSigninActivities);
const publishMock = vi.mocked(publishSigninActivity);

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSessionStore().permissions = Object.values(PERMS);
  const wrapper = mount(SigninActivityPage, { global: { plugins: [pinia], directives: { auth } } });
  await flushPromises();
  return wrapper;
}

describe("SigninActivityPage", () => {
  beforeEach(() => {
    pageMock.mockReset();
    publishMock.mockReset();
    pageMock.mockResolvedValue(
      ok({
        total: 1,
        records: [
          {
            id: 3,
            code: "daily_check",
            name: "每日签到",
            startTime: "2026-08-01T00:00:00Z",
            endTime: "2026-08-31T16:00:00Z",
            status: "DRAFT",
            version: 0,
            pendingRevision: false,
            tiers: [{ day: 1, prizeId: 10 }],
          },
        ],
      }),
    );
  });

  it("lists activities and previews publish confirm", async () => {
    const successSpy = vi.spyOn(message, "success");
    publishMock
      .mockResolvedValueOnce(
        ok({
          requiresConfirm: true,
          message: "新签到将使用新版本，已产生的记录按签到时快照结算",
          id: 3,
          code: "daily_check",
          version: 0,
          status: "DRAFT",
        }),
      )
      .mockResolvedValueOnce(
        ok({
          requiresConfirm: false,
          id: 3,
          code: "daily_check",
          version: 1,
          status: "PUBLISHED",
        }),
      );
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="signin-table"]').text()).toContain("daily_check");
    expect(visibleText(wrapper, "signin-create")).toContain(zhCN.common.create);
    await wrapper.get('[data-testid="signin-publish"]').trigger("click");
    await flushPromises();
    expect(publishMock).toHaveBeenCalledWith(3, { confirm: false, early: true });
    expect(successSpy).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="confirm-message"]').text()).toContain("新签到将使用新版本");
    await wrapper.get('[data-testid="confirm-ok"]').trigger("click");
    await flushPromises();
    expect(publishMock).toHaveBeenNthCalledWith(2, 3, { confirm: true, early: true });
    expect(successSpy).toHaveBeenCalledWith(zhCN.common.saved);
    successSpy.mockRestore();
  });
});
