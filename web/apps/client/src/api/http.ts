import axios, { type AxiosInstance } from "axios";
import type { Result } from "@mkt/shared";
import { ensureDeviceId } from "@/utils/device-id";
import { readPortalToken } from "@/utils/token";

export const DEVICE_HEADER = "X-Device-Id";
export const AUTH_HEADER = "Authorization";
export const PLATFORM_HEADER = "X-Client-Platform";
export const CLIENT_PLATFORM = "WEB";

const SKIP_UNAUTHORIZED = [
  "/api/common/auth/login",
  "/api/common/auth/register",
  "/api/common/captcha",
  "/api/common/auth/username-available",
  "/api/common/track/batch",
  "/api/common/ad/",
  "/api/common/points/balance",
  "/api/common/activity/activities",
  "/api/common/task/list",
];

export type UnauthorizedPayload = {
  code?: string;
  message?: string;
};

let unauthorizedHandler: ((payload: UnauthorizedPayload) => void) | undefined;

export function setUnauthorizedHandler(handler: ((payload: UnauthorizedPayload) => void) | undefined): void {
  unauthorizedHandler = handler;
}

export function shouldSkipUnauthorized(url: string | undefined): boolean {
  if (!url) {
    return false;
  }
  if (SKIP_UNAUTHORIZED.some((path) => url.includes(path))) {
    return true;
  }
  if (/\/api\/common\/activity\/\d+(\?|$)/.test(url)) {
    return true;
  }
  return /\/api\/common\/task\/\d+\/detail/.test(url);
}

export function attachPortalHeaders(
  config: { headers?: Record<string, string> | { set?(name: string, value: string): void } },
  token = readPortalToken(),
  deviceId = ensureDeviceId(),
): void {
  if (!config.headers) {
    return;
  }
  const headers = config.headers;
  if (typeof (headers as { set?: unknown }).set === "function") {
    const settable = headers as { set(name: string, value: string): void };
    if (token) {
      settable.set(AUTH_HEADER, `Bearer ${token}`);
    }
    if (deviceId) {
      settable.set(DEVICE_HEADER, deviceId);
    }
    settable.set(PLATFORM_HEADER, CLIENT_PLATFORM);
    return;
  }
  const record = headers as Record<string, string>;
  if (token) {
    record[AUTH_HEADER] = `Bearer ${token}`;
  }
  if (deviceId) {
    record[DEVICE_HEADER] = deviceId;
  }
  record[PLATFORM_HEADER] = CLIENT_PLATFORM;
}

export function createHttp(): AxiosInstance {
  const instance = axios.create({
    timeout: 15000,
    validateStatus: (status) => status > 0 && status < 500,
  });
  instance.interceptors.request.use((config) => {
    attachPortalHeaders(config);
    return config;
  });
  instance.interceptors.response.use((response) => {
    if (response.status === 401 && !shouldSkipUnauthorized(response.config.url)) {
      const body = response.data as Result | undefined;
      unauthorizedHandler?.({
        code: typeof body?.code === "string" ? body.code : undefined,
        message: body?.message,
      });
    }
    return response;
  });
  return instance;
}

export const http = createHttp();

export function compactParams(
  params?: Record<string, unknown>,
): Record<string, string | number | boolean> | undefined {
  if (!params) {
    return undefined;
  }
  const out: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") {
      continue;
    }
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      out[key] = value;
    }
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

export async function request<T>(
  method: string,
  url: string,
  body?: unknown,
  params?: Record<string, unknown>,
): Promise<Result<T>> {
  const response = await http.request<Result<T>>({
    method,
    url,
    data: body,
    params: compactParams(params),
  });
  return response.data;
}
