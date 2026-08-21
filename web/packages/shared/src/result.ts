/** Appendix C envelope. Success `code` is number 0; failure `code` is a string. */
export type Result<T = unknown> = {
  code: 0 | string;
  message?: string;
  data?: T | null;
  traceId?: string;
};

export function isOk<T>(r: Result<T> | null | undefined): r is Result<T> & { code: 0 } {
  return r != null && r.code === 0;
}

export function isFail(
  r: Result | null | undefined,
): r is Result & { code: string; message: string } {
  return r != null && typeof r.code === "string";
}
