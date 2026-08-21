import type { Result } from "@mkt/shared";
import { isFail, isOk } from "@mkt/shared";

export type PageFeedback = {
  message: string;
  traceId?: string;
};

export function failFeedback(result: Result): PageFeedback {
  if (isFail(result)) {
    return { message: result.message, traceId: result.traceId };
  }
  return { message: "请求失败" };
}

export function okOrFeedback<T>(result: Result<T>): { ok: true; data: T | null | undefined } | { ok: false; feedback: PageFeedback } {
  if (isOk(result)) {
    return { ok: true, data: result.data };
  }
  return { ok: false, feedback: failFeedback(result) };
}
