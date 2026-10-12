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

describe("portal-error UX-1 login/register copy", () => {
  it("maps invalid credential", () => {
    expect(
      resultMessage({ code: "auth.login.invalid-credential", message: "用户名或密码错误" }),
    ).toBe(zhCN.login.invalidCredential);
  });

  it("maps captcha invalid/expired without exposing codes", () => {
    expect(resultMessage({ code: "auth.captcha.invalid", message: "auth.captcha.invalid" })).toBe(
      zhCN.login.captchaInvalid,
    );
    expect(resultMessage({ code: "auth.captcha.expired", message: "验证码已过期" })).toBe(
      zhCN.login.captchaExpired,
    );
  });

  it("maps login locked / rate-limit / disabled / risk", () => {
    expect(resultMessage({ code: "auth.login.locked", message: "x" })).toBe(zhCN.login.locked);
    expect(resultMessage({ code: "auth.login.rate-limited", message: "x" })).toBe(
      zhCN.login.rateLimited,
    );
    expect(resultMessage({ code: "auth.account.disabled", message: "x" })).toBe(
      zhCN.login.accountDisabled,
    );
    expect(resultMessage({ code: "risk.blocked.login", message: "x" })).toBe(zhCN.login.riskBlocked);
  });

  it("maps register username/password/risk/rate", () => {
    expect(resultMessage({ code: "auth.username.duplicate", message: "x" })).toBe(
      zhCN.register.usernameDuplicate,
    );
    expect(resultMessage({ code: "auth.password.policy-violated", message: "x" })).toBe(
      zhCN.register.passwordPolicy,
    );
    expect(resultMessage({ code: "auth.register.rate-limited", message: "x" })).toBe(
      zhCN.register.rateLimited,
    );
    expect(resultMessage({ code: "risk.blocked.register", message: "x" })).toBe(
      zhCN.register.riskBlocked,
    );
  });

  it("maps 5xx / param-invalid", () => {
    expect(resultMessage({ code: "common.server-error", message: "服务异常，请稍后重试" })).toBe(
      zhCN.common.serverBusy,
    );
    expect(resultMessage({ code: "common.param-invalid", message: "参数不合法" })).toBe(
      zhCN.common.paramInvalid,
    );
  });

  it("does not toast raw auth.* codes as message", () => {
    expect(resultMessage({ code: "auth.unknown", message: "auth.unknown" })).toBe(
      zhCN.common.networkError,
    );
  });
});
