import type { paths } from "@mkt/shared/openapi/admin";
import type { Result } from "@mkt/shared";
import { request } from "./http";

type Json<T> = T extends { content: { "application/json": infer B } } ? B : never;
type Star<T> = T extends { content: { "*/*": infer B } } ? B : never;
type Envelope<T> = T extends { data?: infer D } ? D : unknown;

export type AdminLoginBody = Json<paths["/admin/auth/login"]["post"]["requestBody"]>;
export type AdminLoginResult = Star<paths["/admin/auth/login"]["post"]["responses"][200]>;
export type AdminLoginData = NonNullable<Envelope<AdminLoginResult>>;
export type CaptchaResult = Star<paths["/admin/captcha"]["get"]["responses"][200]>;
export type CaptchaData = NonNullable<Envelope<CaptchaResult>>;
export type AdminMenusResult = Star<paths["/admin/auth/menus"]["get"]["responses"][200]>;
export type AdminMenuNode = NonNullable<Envelope<AdminMenusResult>> extends Array<infer N>
  ? N
  : never;
export type AdminProfileResult = Star<paths["/admin/auth/profile"]["get"]["responses"][200]>;
export type AdminProfileData = NonNullable<Envelope<AdminProfileResult>>;

export const CAPTCHA_ERROR_CODES = new Set(["auth.captcha.invalid", "auth.captcha.expired"]);

export function fetchCaptcha(): Promise<Result<CaptchaData>> {
  return request<CaptchaData>("GET", "/admin/captcha");
}

export function login(body: AdminLoginBody): Promise<Result<AdminLoginData>> {
  return request<AdminLoginData>("POST", "/admin/auth/login", body);
}

export function logout(): Promise<Result<{ ok?: boolean }>> {
  return request("POST", "/admin/auth/logout");
}

export function fetchMenus(): Promise<Result<AdminMenuNode[]>> {
  return request<AdminMenuNode[]>("GET", "/admin/auth/menus");
}

export function fetchProfile(): Promise<Result<AdminProfileData>> {
  return request<AdminProfileData>("GET", "/admin/auth/profile");
}
