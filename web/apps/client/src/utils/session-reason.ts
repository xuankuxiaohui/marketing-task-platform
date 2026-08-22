import { zhCN } from "@/locales/zh-CN";

export const SESSION_CODES = {
  kickedConcurrent: "auth.session.kicked-concurrent",
  kickedAdmin: "auth.session.kicked-admin",
  expired: "auth.session.expired",
  missing: "auth.session.missing",
} as const;

export function sessionMessage(code: string | undefined, fallback?: string): string {
  switch (code) {
    case SESSION_CODES.kickedConcurrent:
      return zhCN.session.kickedConcurrent;
    case SESSION_CODES.kickedAdmin:
      return zhCN.session.kickedAdmin;
    case SESSION_CODES.expired:
      return zhCN.session.expired;
    case SESSION_CODES.missing:
      return zhCN.session.missing;
    default:
      return fallback || zhCN.session.missing;
  }
}

export function isGuestSessionCode(code: unknown): boolean {
  return code === SESSION_CODES.missing || code === SESSION_CODES.expired;
}
