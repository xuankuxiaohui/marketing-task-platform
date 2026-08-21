/** Shared AuthRateLimiter IP bucket: captcha, username-available, login, register (20/60s). */
export const LOGIN_RATE_LIMITED = "auth.login.rate-limited";
export const REGISTER_RATE_LIMITED = "auth.register.rate-limited";

/** 3 retries after a rate-limited response. */
export const AUTH_RATE_LIMIT_BACKOFFS_MS = [5_000, 10_000, 20_000] as const;

export function isAuthRateLimited(payload: { code?: unknown } | null | undefined): boolean {
  return payload?.code === LOGIN_RATE_LIMITED || payload?.code === REGISTER_RATE_LIMITED;
}

export async function sleep(ms: number): Promise<void> {
  await new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });
}

export async function withAuthRateLimitRetry<T>(
  run: () => Promise<T>,
  codeOf: (value: T) => unknown,
): Promise<T> {
  let last = await run();
  for (const wait of AUTH_RATE_LIMIT_BACKOFFS_MS) {
    if (!isAuthRateLimited({ code: codeOf(last) })) {
      return last;
    }
    await sleep(wait);
    last = await run();
  }
  return last;
}
