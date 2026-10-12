import { showFailToast } from "vant";
import { isFail, type Result } from "@mkt/shared";
import { zhCN } from "@/locales/zh-CN";

/** UX-2: claim/start reject codes → human copy (reason + when retry). Never surface raw codes. */
const CLAIM_REJECT_COPY: Record<string, string> = {
  "task.claim.daily-limit": zhCN.task.claimDailyLimit,
  "risk.blocked.generic": zhCN.task.claimRiskBlocked,
  "risk.blocked.account-restricted": zhCN.task.claimAccountRestricted,
};

export function resultMessage(result: Result, fallback = zhCN.common.networkError): string {
  if (!isFail(result)) {
    return fallback;
  }
  const mapped = CLAIM_REJECT_COPY[result.code];
  if (mapped) {
    return mapped;
  }
  const msg = result.message?.trim();
  // Ban leaking technical codes as the toast body
  if (msg && msg !== result.code && !msg.startsWith("task.") && !msg.startsWith("risk.")) {
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
