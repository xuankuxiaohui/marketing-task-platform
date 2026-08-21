import { apiBase, loadDotEnv } from "./env";
import { waitRedisGet } from "./redis";

export type Json = Record<string, unknown>;

export type AdminSession = {
  cookie: string;
  csrfToken: string;
  password: string;
};

/** Distinct from the init password and still satisfies R1.5. */
export function unlockedAdminPassword(initPassword: string): string {
  return initPassword.endsWith("!") ? `${initPassword.slice(0, -1)}?` : `${initPassword}!`;
}

function cookieHeader(setCookies: string[]): string {
  return setCookies
    .map((entry) => entry.split(";")[0])
    .filter(Boolean)
    .join("; ");
}

export async function api<T = Json>(
  path: string,
  init: RequestInit & { cookie?: string; csrf?: string } = {},
): Promise<{ status: number; body: T; cookie: string }> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body != null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (init.cookie) {
    headers.set("Cookie", init.cookie);
  }
  if (init.csrf) {
    headers.set("X-CSRF-Token", init.csrf);
  }
  if (path.startsWith("/api/")) {
    headers.set("X-Device-Id", headers.get("X-Device-Id") ?? "e2e-device");
    headers.set("X-Client-Platform", "WEB");
  }
  const response = await fetch(`${apiBase()}${path}`, { ...init, headers });
  const raw = await response.text();
  const body = (raw ? JSON.parse(raw) : {}) as T;
  const setCookies =
    typeof response.headers.getSetCookie === "function" ? response.headers.getSetCookie() : [];
  return { status: response.status, body, cookie: cookieHeader(setCookies) };
}

function requireOk<T extends { code?: unknown; message?: string }>(payload: T, label: string): T {
  if (payload.code !== 0) {
    throw new Error(`${label} failed: ${JSON.stringify(payload)}`);
  }
  return payload;
}

export async function captcha(path: string, realm: "admin" | "portal"): Promise<{ captchaId: string; code: string }> {
  const { body } = await api<{ code: unknown; data?: { captchaId?: string } }>(path);
  requireOk(body, path);
  const captchaId = body.data?.captchaId;
  if (!captchaId) {
    throw new Error(`captchaId missing from ${path}`);
  }
  const code = await waitRedisGet(`captcha:${realm}:${captchaId}`);
  return { captchaId, code };
}

type AdminLoginBody = {
  code?: unknown;
  message?: string;
  data?: { csrfToken?: string; mustChangePassword?: boolean };
};

async function loginAdminOnce(password: string): Promise<{ body: AdminLoginBody; cookie: string }> {
  const { captchaId, code } = await captcha("/admin/captcha", "admin");
  return api<AdminLoginBody>("/admin/auth/login", {
    method: "POST",
    body: JSON.stringify({
      username: "admin",
      password,
      captchaId,
      captchaCode: code,
    }),
  });
}

export async function adminLogin(): Promise<AdminSession> {
  const env = loadDotEnv();
  const initPassword = env.MKT_INIT_ADMIN_PASSWORD;
  const unlockedPassword = unlockedAdminPassword(initPassword);
  let { body, cookie } = await loginAdminOnce(initPassword);
  let usedPassword = initPassword;
  if (body.code !== 0) {
    const retry = await loginAdminOnce(unlockedPassword);
    body = retry.body;
    cookie = retry.cookie;
    usedPassword = unlockedPassword;
  }
  requireOk(body, "admin login");
  const csrfToken = body.data?.csrfToken;
  if (!cookie || !csrfToken) {
    throw new Error("admin login missing cookie/csrf");
  }
  if (body.data?.mustChangePassword) {
    const changed = await api<{ code?: unknown; message?: string }>("/admin/auth/password", {
      method: "PUT",
      cookie,
      csrf: csrfToken,
      body: JSON.stringify({
        oldPassword: usedPassword,
        newPassword: unlockedPassword,
      }),
    });
    requireOk(changed.body, "admin change password");
    const retry = await loginAdminOnce(unlockedPassword);
    body = retry.body;
    cookie = retry.cookie;
    requireOk(body, "admin login after change");
    const nextCsrf = body.data?.csrfToken;
    if (!cookie || !nextCsrf) {
      throw new Error("admin login after change missing cookie/csrf");
    }
    return { cookie, csrfToken: nextCsrf, password: unlockedPassword };
  }
  return { cookie, csrfToken, password: usedPassword };
}

export async function ensurePrize(session: AdminSession, code: string, claimMode: "AUTO" | "MANUAL"): Promise<number> {
  const page = await api<{ code: unknown; data?: { records?: Array<{ id?: number }> } }>(
    `/admin/reward/prizes?code=${encodeURIComponent(code)}&page=1&pageSize=5`,
    { cookie: session.cookie },
  );
  requireOk(page.body, "prize page");
  const existing = page.body.data?.records?.[0]?.id;
  if (existing != null) {
    return Number(existing);
  }
  const created = await api<{ code: unknown; data?: { id?: number } }>("/admin/reward/prizes", {
    method: "POST",
    cookie: session.cookie,
    csrf: session.csrfToken,
    body: JSON.stringify({
      code,
      name: `${code} points`,
      categoryCode: "POINTS",
      typeParams: { points: 10 },
      totalStock: 100000,
      dailyClaimLimit: 0,
      totalClaimLimit: 0,
      claimMode,
    }),
  });
  requireOk(created.body, "prize create");
  const prizeId = created.body.data?.id;
  if (prizeId == null) {
    throw new Error("prize id missing");
  }
  const enabled = await api(`/admin/reward/prizes/${prizeId}/enable`, {
    method: "POST",
    cookie: session.cookie,
    csrf: session.csrfToken,
    body: JSON.stringify({ confirm: true }),
  });
  requireOk(enabled.body as { code?: unknown }, "prize enable");
  return Number(prizeId);
}

export type TaskSeed = {
  code: string;
  name: string;
  steps: Array<Record<string, unknown>>;
  transitions: Array<Record<string, unknown>>;
};

export async function ensurePublishedTask(session: AdminSession, seed: TaskSeed): Promise<number> {
  const page = await api<{
    code: unknown;
    data?: { records?: Array<{ id?: number; status?: string }> };
  }>(`/admin/task/definitions?code=${encodeURIComponent(seed.code)}&page=1&pageSize=5`, {
    cookie: session.cookie,
  });
  requireOk(page.body, "task page");
  const row = page.body.data?.records?.[0];
  if (row?.id != null) {
    if (row.status !== "PUBLISHED") {
      const published = await api(`/admin/task/definitions/${row.id}/publish`, {
        method: "POST",
        cookie: session.cookie,
        csrf: session.csrfToken,
        body: JSON.stringify({ confirm: true }),
      });
      requireOk(published.body as { code?: unknown }, "task publish");
    }
    return Number(row.id);
  }
  const saved = await api<{ code: unknown; data?: { id?: number } }>("/admin/task/definitions/save-aggregate", {
    method: "POST",
    cookie: session.cookie,
    csrf: session.csrfToken,
    body: JSON.stringify({
      code: seed.code,
      name: seed.name,
      cycleType: "NONE",
      sortWeight: 0,
      gray: { type: "NONE" },
      steps: seed.steps,
      transitions: seed.transitions,
      actions: [],
    }),
  });
  requireOk(saved.body, "task save");
  const taskId = saved.body.data?.id;
  if (taskId == null) {
    throw new Error("task id missing");
  }
  const published = await api(`/admin/task/definitions/${taskId}/publish`, {
    method: "POST",
    cookie: session.cookie,
    csrf: session.csrfToken,
    body: JSON.stringify({ confirm: true }),
  });
  requireOk(published.body as { code?: unknown }, "task publish");
  return Number(taskId);
}

export async function registerPortalUser(username: string, password: string): Promise<{ token: string; userId: number }> {
  const available = await api<{ code: unknown; data?: { available?: boolean } }>(
    `/api/common/auth/username-available?username=${encodeURIComponent(username)}`,
  );
  requireOk(available.body, "username-available");
  const { captchaId, code } = await captcha("/api/common/captcha", "portal");
  const path = available.body.data?.available === false ? "/api/common/auth/login" : "/api/common/auth/register";
  const auth = await api<{ code: unknown; data?: { token?: string; userId?: number } }>(path, {
    method: "POST",
    body: JSON.stringify({
      username,
      password,
      captchaId,
      captchaCode: code,
    }),
  });
  requireOk(auth.body, path);
  const token = auth.body.data?.token;
  const userId = auth.body.data?.userId;
  if (!token || userId == null) {
    throw new Error("portal auth missing token");
  }
  return { token, userId: Number(userId) };
}

export async function startTask(token: string, taskId: number): Promise<number> {
  const started = await api<{ code: unknown; data?: { instanceId?: number } }>(`/api/common/task/${taskId}/start`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: "{}",
  });
  requireOk(started.body, "task start");
  const instanceId = started.body.data?.instanceId;
  if (instanceId == null) {
    throw new Error("instanceId missing");
  }
  return Number(instanceId);
}

export const E2E_PORTAL_PASSWORD = "E2euser1";
