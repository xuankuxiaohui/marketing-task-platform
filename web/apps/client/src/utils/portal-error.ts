import { showFailToast } from "vant";
import { isFail, type Result } from "@mkt/shared";
import { zhCN } from "@/locales/zh-CN";

export function resultMessage(result: Result, fallback = zhCN.common.networkError): string {
  return isFail(result) && result.message ? result.message : fallback;
}

export function showPortalFail(result: Result, fallback = zhCN.common.networkError): void {
  showFailToast(resultMessage(result, fallback));
}

export function showNetworkFail(): void {
  showFailToast(zhCN.common.networkError);
}
