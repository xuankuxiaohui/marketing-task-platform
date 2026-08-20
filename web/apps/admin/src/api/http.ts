import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from "axios";
import type { Result } from "@mkt/shared";

export const CSRF_COOKIE = "csrfToken";
export const CSRF_HEADER = "X-CSRF-Token";

const SKIP_UNAUTHORIZED = ["/admin/auth/login", "/admin/captcha"];

let unauthorizedHandler: (() => void) | undefined;

export function setUnauthorizedHandler(handler: (() => void) | undefined): void {
  unauthorizedHandler = handler;
}

export function readCookie(name: string, cookieSource = typeof document === "undefined" ? "" : document.cookie): string | undefined {
  if (!cookieSource) {
    return undefined;
  }
  const parts = cookieSource.split(";");
  for (const part of parts) {
    const trimmed = part.trim();
    const eq = trimmed.indexOf("=");
    if (eq < 0) {
      continue;
    }
    const key = trimmed.slice(0, eq);
    if (key === name) {
      return decodeURIComponent(trimmed.slice(eq + 1));
    }
  }
  return undefined;
}

export function isUnsafeMethod(method: string | undefined): boolean {
  const m = (method ?? "get").toUpperCase();
  return m !== "GET" && m !== "HEAD" && m !== "OPTIONS";
}

export function shouldSkipUnauthorized(url: string | undefined): boolean {
  if (!url) {
    return false;
  }
  return SKIP_UNAUTHORIZED.some((path) => url.includes(path));
}

export function attachCsrf(
  config: { method?: string; headers?: InternalAxiosRequestConfig["headers"] | Record<string, string> },
  cookieSource?: string,
): void {
  if (!isUnsafeMethod(config.method) || !config.headers) {
    return;
  }
  const csrf = readCookie(CSRF_COOKIE, cookieSource);
  if (csrf) {
    config.headers[CSRF_HEADER] = csrf;
  }
}

export function createHttp(): AxiosInstance {
  const instance = axios.create({
    timeout: 15000,
    withCredentials: true,
    validateStatus: (status) => status > 0 && status < 500,
  });
  instance.interceptors.request.use((config) => {
    attachCsrf(config);
    return config;
  });
  instance.interceptors.response.use((response) => {
    if (response.status === 401 && !shouldSkipUnauthorized(response.config.url)) {
      unauthorizedHandler?.();
    }
    return response;
  });
  return instance;
}

export const http = createHttp();

export async function request<T>(
  method: string,
  url: string,
  body?: unknown,
): Promise<Result<T>> {
  const response = await http.request<Result<T>>({ method, url, data: body });
  return response.data;
}
