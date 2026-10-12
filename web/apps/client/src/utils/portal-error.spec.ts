import { describe, expect, it, vi } from "vitest";
import { showFailToast } from "vant";
import { resultMessage, showPortalFail } from "@/utils/portal-error";
import { zhCN } from "@/locales/zh-CN";

vi.mock("vant", () => ({
  showFailToast: vi.fn(),
}));

describe("portal-error UX-2 claim reject copy", () => {
  it("maps daily-limit to human retry copy", () => {
    expect(
      resultMessage({ code: "task.claim.daily-limit", message: "今日领取次数已达上限" }),
    ).toBe(zhCN.task.claimDailyLimit);
  });

  it("maps risk.blocked.generic without exposing code", () => {
    expect(resultMessage({ code: "risk.blocked.generic", message: "risk.blocked.generic" })).toBe(
      zhCN.task.claimRiskBlocked,
    );
  });

  it("maps account-restricted", () => {
    expect(
      resultMessage({ code: "risk.blocked.account-restricted", message: "账号受限" }),
    ).toBe(zhCN.task.claimAccountRestricted);
  });

  it("keeps unrelated human messages", () => {
    expect(resultMessage({ code: "reward.claim.failed", message: "领取暂不可用" })).toBe(
      "领取暂不可用",
    );
  });

  it("falls back when message is a raw code", () => {
    expect(resultMessage({ code: "unknown.x", message: "unknown.x" })).toBe(
      zhCN.common.networkError,
    );
  });

  it("toasts mapped copy via showPortalFail", () => {
    const toast = vi.mocked(showFailToast);
    toast.mockReset();
    showPortalFail({ code: "task.claim.daily-limit", message: "今日领取次数已达上限" });
    expect(toast).toHaveBeenCalledWith(zhCN.task.claimDailyLimit);
  });
});
