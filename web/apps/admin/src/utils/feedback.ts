import { message } from "ant-design-vue";
import type { Result } from "@mkt/shared";
import { isFail, isOk } from "@mkt/shared";
import { zhCN } from "@/locales/zh-CN";

export type PageFeedback = {
  message: string;
  traceId?: string;
};

export type WriteNotice = {
  success: (text: string) => void;
  error: (text: string) => void;
};

const defaultNotice: WriteNotice = {
  success: (text) => {
    message.success(text);
  },
  error: (text) => {
    message.error(text, 8);
  },
};

let notice: WriteNotice = defaultNotice;

export function setWriteNotice(next: WriteNotice | null): void {
  notice = next ?? defaultNotice;
}

export function failFeedback(result: Result): PageFeedback {
  if (isFail(result)) {
    return { message: result.message, traceId: result.traceId };
  }
  return { message: "请求失败" };
}

export function formatWriteError(feedback: PageFeedback): string {
  if (feedback.traceId) {
    return `${feedback.message} · ${feedback.traceId}`;
  }
  return feedback.message;
}

export function okOrFeedback<T>(result: Result<T>): { ok: true; data: T | null | undefined } | { ok: false; feedback: PageFeedback } {
  if (isOk(result)) {
    return { ok: true, data: result.data };
  }
  return { ok: false, feedback: failFeedback(result) };
}

/** Write-path parse: success/error toasts sit above a modal, not under the page banner. */
export function writeOrFeedback<T>(
  result: Result<T>,
  successText: string = zhCN.common.saved,
): { ok: true; data: T | null | undefined } | { ok: false; feedback: PageFeedback } {
  const parsed = okOrFeedback(result);
  if (parsed.ok) {
    notice.success(successText);
    return parsed;
  }
  notice.error(formatWriteError(parsed.feedback));
  return parsed;
}
