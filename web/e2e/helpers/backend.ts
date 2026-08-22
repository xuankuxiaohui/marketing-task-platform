import { existsSync, readFileSync } from "node:fs";
import { apiBase, loadDotEnv, statePath } from "./env";
import { isAuthRateLimited, withAuthRateLimitRetry } from "./rateLimit";
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

export async function captcha(
  path: string,
  realm: "admin" | "portal",
): Promise<{ captchaId: string; code: string }> {
  const body = await withAuthRateLimitRetry(
    async () => {
      const { body } = await api<{ code: unknown; data?: { captchaId?: string } }>(path);
      return body;
    },
    (payload) => payload.code,
  );
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
  return withAuthRateLimitRetry(
    async () => {
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
    },
    (result) => result.body.code,
  );
}

function peekKnownAdminPassword(): string | undefined {
  const path = statePath();
  if (!existsSync(path)) {
    return undefined;
  }
  try {
    const state = JSON.parse(readFileSync(path, "utf8")) as { adminPassword?: unknown };
    return typeof state.adminPassword === "string" && state.adminPassword
      ? state.adminPassword
      : undefined;
  } catch {
    return undefined;
  }
}

export async function adminLogin(): Promise<AdminSession> {
  const env = loadDotEnv();
  const initPassword = env.MKT_INIT_ADMIN_PASSWORD;
  const unlockedPassword = unlockedAdminPassword(initPassword);
  const known = peekKnownAdminPassword();
  const first = known === unlockedPassword ? unlockedPassword : initPassword;
  const second = first === initPassword ? unlockedPassword : initPassword;
  let { body, cookie } = await loginAdminOnce(first);
  let usedPassword = first;
  if (isAuthRateLimited(body)) {
    requireOk(body, "admin login");
  }
  if (body.code !== 0) {
    const retry = await loginAdminOnce(second);
    body = retry.body;
    cookie = retry.cookie;
    usedPassword = second;
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

export async function ensurePrize(
  session: AdminSession,
  code: string,
  claimMode: "AUTO" | "MANUAL",
): Promise<number> {
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
  const saved = await api<{ code: unknown; data?: { id?: number } }>(
    "/admin/task/definitions/save-aggregate",
    {
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
    },
  );
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

export type ActivitySeed = {
  code: string;
  name: string;
  taskId: number;
};

function activitySaveBody(seed: ActivitySeed, id?: number): Record<string, unknown> {
  return {
    ...(id != null ? { id } : {}),
    code: seed.code,
    name: seed.name,
    startTime: "2020-01-01T00:00:00Z",
    endTime: "2099-12-31T23:59:59Z",
    richText: "<p>e2e core</p>",
    gray: { type: "NONE" },
    submodules: [{ type: "TASK", refId: seed.taskId, sort: 0 }],
    newUserOnly: false,
    newUserDays: 7,
  };
}

export async function ensurePublishedActivity(session: AdminSession, seed: ActivitySeed): Promise<number> {
  const page = await api<{
    code: unknown;
    data?: { records?: Array<{ id?: number; status?: string }> };
  }>(`/admin/activity/activities?code=${encodeURIComponent(seed.code)}&page=1&pageSize=5`, {
    cookie: session.cookie,
  });
  requireOk(page.body, "activity page");
  const row = page.body.data?.records?.[0];
  let activityId: number;
  if (row?.id != null) {
    activityId = Number(row.id);
    if (row.status === "PUBLISHED") {
      return activityId;
    }
    const saved = await api("/admin/activity/activities", {
      method: "POST",
      cookie: session.cookie,
      csrf: session.csrfToken,
      body: JSON.stringify(activitySaveBody(seed, activityId)),
    });
    requireOk(saved.body as { code?: unknown }, "activity save");
  } else {
    const saved = await api<{ code: unknown; data?: { id?: number } }>("/admin/activity/activities", {
      method: "POST",
      cookie: session.cookie,
      csrf: session.csrfToken,
      body: JSON.stringify(activitySaveBody(seed)),
    });
    requireOk(saved.body, "activity save");
    const id = saved.body.data?.id;
    if (id == null) {
      throw new Error("activity id missing");
    }
    activityId = Number(id);
  }
  const published = await api(`/admin/activity/activities/${activityId}/publish`, {
    method: "POST",
    cookie: session.cookie,
    csrf: session.csrfToken,
    body: JSON.stringify({ confirm: true, early: true }),
  });
  requireOk(published.body as { code?: unknown }, "activity publish");
  return activityId;
}

export async function registerPortalUser(
  username: string,
  password: string,
): Promise<{ token: string; userId: number }> {
  const available = await withAuthRateLimitRetry(
    () =>
      api<{ code: unknown; data?: { available?: boolean } }>(
        `/api/common/auth/username-available?username=${encodeURIComponent(username)}`,
      ),
    (result) => result.body.code,
  );
  requireOk(available.body, "username-available");
  const path =
    available.body.data?.available === false
      ? "/api/common/auth/login"
      : "/api/common/auth/register";
  const auth = await withAuthRateLimitRetry(
    async () => {
      const { captchaId, code } = await captcha("/api/common/captcha", "portal");
      return api<{ code: unknown; data?: { token?: string; userId?: number } }>(path, {
        method: "POST",
        body: JSON.stringify({
          username,
          password,
          captchaId,
          captchaCode: code,
        }),
      });
    },
    (result) => result.body.code,
  );
  requireOk(auth.body, path);
  const token = auth.body.data?.token;
  const userId = auth.body.data?.userId;
  if (!token || userId == null) {
    throw new Error("portal auth missing token");
  }
  return { token, userId: Number(userId) };
}

export async function startTask(token: string, taskId: number): Promise<number> {
  const started = await api<{ code: unknown; data?: { instanceId?: number } }>(
    `/api/common/task/${taskId}/start`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: "{}",
    },
  );
  requireOk(started.body, "task start");
  const instanceId = started.body.data?.instanceId;
  if (instanceId == null) {
    throw new Error("instanceId missing");
  }
  return Number(instanceId);
}

export const E2E_PORTAL_PASSWORD = "E2euser1";
