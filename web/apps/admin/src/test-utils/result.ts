import type { Result } from "@mkt/shared";

export function ok<T>(data: T): Result<T> {
  return { code: 0, message: "ok", data };
}

export function fail(code: string, message: string, traceId = "trace-1"): Result {
  return { code, message, traceId };
}
