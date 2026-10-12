import { showFailToast } from "vant";
import { isFail, type Result } from "@mkt/shared";
import { zhCN } from "@/locales/zh-CN";

/** UX-2: claim/start reject codes → human copy. Never surface raw codes. */
const CLAIM_REJECT_COPY: Record<string, string> = {
  "task.claim.daily-limit": zhCN.task.claimDailyLimit,
  "risk.blocked.generic": zhCN.task.claimRiskBlocked,
  "risk.blocked.account-restricted": zhCN.task.claimAccountRestricted,
};

/**
 * UX-1 (tight): login/register — 账号错 / 验证码错 only.
 * Network/timeout handled by callers via zhCN.common.networkError.
 * Never surface raw codes.
 */
const AUTH_ERROR_COPY: Record<string, string> = {
  "auth.login.invalid-credential": zhCN.login.invalidCredential,
  "auth.captcha.invalid": zhCN.login.captchaInvalid,
  "auth.captcha.expired": zhCN.login.captchaExpired,
};

const CODE_COPY: Record<string, string> = {
  ...CLAIM_REJECT_COPY,
  ...AUTH_ERROR_COPY,
};

function looksLikeErrorCode(msg: string): boolean {
  return (
    msg.includes(".") &&
    (msg.startsWith("task.") ||
      msg.startsWith("risk.") ||
      msg.startsWith("auth.") ||
      msg.startsWith("common.") ||
      msg.startsWith("reward.") ||
      /^[a-z0-9]+(\.[a-z0-9-]+)+$/.test(msg))
  );
}

export function resultMessage(result: Result, fallback = zhCN.common.networkError): string {
  if (!isFail(result)) {
    return fallback;
  }
  const mapped = CODE_COPY[result.code];
  if (mapped) {
    return mapped;
  }
  const msg = result.message?.trim();
  if (msg && msg !== result.code && !looksLikeErrorCode(msg)) {
    return msg;
  }
  return fallback;
}

export function showPortalFail(result: Result, fallback = zhCN.common.networkError): void {
  showFailToast(resultMessage(result, fallback));
}

export function showNetworkFail(): void {
  showFailToast(zhCN.common.networkError);
}
