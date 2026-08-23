import type { UnauthorizedPayload } from "@/api/http";
import { LOGIN_ROUTE } from "@/router/guards";
import { SESSION_CODES, sessionMessage } from "@/utils/session-reason";

export type UnauthorizedContext = {
  currentPath: string;
  reset: () => void;
  toast: (message: string) => void;
  openOverlay: (opts: { redirect: string; code?: string; message?: string }) => void;
};

export function isKickSessionCode(code: string | undefined): boolean {
  return code === SESSION_CODES.kickedConcurrent || code === SESSION_CODES.kickedAdmin;
}

export function handlePortalUnauthorized(payload: UnauthorizedPayload, ctx: UnauthorizedContext): void {
  const kicked = isKickSessionCode(payload.code);
  ctx.reset();
  if (kicked) {
    ctx.toast(sessionMessage(payload.code, payload.message));
  }
  if (ctx.currentPath === LOGIN_ROUTE || ctx.currentPath === "/register") {
    return;
  }
  ctx.openOverlay({
    redirect: ctx.currentPath,
    code: payload.code,
    message: kicked ? sessionMessage(payload.code, payload.message) : undefined,
  });
}
