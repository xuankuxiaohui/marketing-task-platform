import { zhCN } from "@/locales/zh-CN";
import type { CurrentStepView, PlatformActionView } from "@/api/task";

export type StepActionMode = "click" | "callback-go" | "callback-wait" | "progress" | "none";

export type StepActionUi = {
  mode: StepActionMode;
  buttonText: string;
  disabled: boolean;
};

export function stepActionUi(
  step: Pick<CurrentStepView, "type" | "action"> | undefined,
  waitingConfirm = false,
): StepActionUi {
  const type = step?.type ?? "";
  const action = step?.action;
  const actionType = action?.actionType ?? "NONE";
  const custom = action?.buttonText?.trim();
  if (type === "PROGRESS") {
    return { mode: "progress", buttonText: "", disabled: true };
  }
  if (type === "CALLBACK") {
    if (waitingConfirm || actionType === "NONE" || action == null) {
      return { mode: "callback-wait", buttonText: zhCN.task.waitConfirm, disabled: true };
    }
    return { mode: "callback-go", buttonText: custom || zhCN.task.goExternal, disabled: false };
  }
  if (type === "CLICK") {
    return { mode: "click", buttonText: custom || zhCN.task.goComplete, disabled: false };
  }
  if (action == null || actionType === "NONE") {
    return { mode: "none", buttonText: zhCN.task.noneAction, disabled: true };
  }
  return { mode: "click", buttonText: custom || zhCN.task.goComplete, disabled: false };
}

export function actionHref(action?: PlatformActionView | null): string | undefined {
  const params = action?.params;
  if (!params) {
    return undefined;
  }
  if (action.actionType === "LINK") {
    const url = params.url;
    return typeof url === "string" && url.startsWith("https://") ? url : undefined;
  }
  if (action.actionType === "SCHEME") {
    const scheme = params.scheme;
    return typeof scheme === "string" && scheme.length > 0 && scheme.length <= 128 ? scheme : undefined;
  }
  return undefined;
}

export function actionRouteName(action?: PlatformActionView | null): string | undefined {
  if (action?.actionType !== "ROUTE") {
    return undefined;
  }
  const route = action.params?.route;
  return typeof route === "string" && route.length > 0 ? route : undefined;
}
