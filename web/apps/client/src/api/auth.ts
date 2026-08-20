import type { paths } from "@mkt/shared/openapi/portal";
import type { Result } from "@mkt/shared";
import { request } from "./http";

type Json<T> = T extends { content: { "application/json": infer B } } ? B : never;
type Star<T> = T extends { content: { "*/*": infer B } } ? B : never;
type Envelope<T> = T extends { data?: infer D } ? D : unknown;

export type PortalLoginBody = Json<paths["/api/common/auth/login"]["post"]["requestBody"]>;
export type PortalRegisterBody = Json<paths["/api/common/auth/register"]["post"]["requestBody"]>;
export type PortalAuthResult = Star<paths["/api/common/auth/login"]["post"]["responses"][200]>;
export type PortalAuthData = NonNullable<Envelope<PortalAuthResult>>;
export type CaptchaResult = Star<paths["/api/common/captcha"]["get"]["responses"][200]>;
export type CaptchaData = NonNullable<Envelope<CaptchaResult>>;
export type UsernameAvailableResult = Star<
  paths["/api/common/auth/username-available"]["get"]["responses"][200]
>;
export type UsernameAvailableData = NonNullable<Envelope<UsernameAvailableResult>>;
export type PortalProfileResult = Star<paths["/api/common/auth/profile"]["get"]["responses"][200]>;
export type PortalProfileData = NonNullable<Envelope<PortalProfileResult>>;
export type PortalNicknameBody = Json<paths["/api/common/auth/profile"]["put"]["requestBody"]>;
export type PortalPasswordBody = Json<paths["/api/common/auth/password"]["put"]["requestBody"]>;

export const CAPTCHA_ERROR_CODES = new Set(["auth.captcha.invalid", "auth.captcha.expired"]);

export function fetchCaptcha(): Promise<Result<CaptchaData>> {
  return request<CaptchaData>("GET", "/api/common/captcha");
}

export function login(body: PortalLoginBody): Promise<Result<PortalAuthData>> {
  return request<PortalAuthData>("POST", "/api/common/auth/login", body);
}

export function register(body: PortalRegisterBody): Promise<Result<PortalAuthData>> {
  return request<PortalAuthData>("POST", "/api/common/auth/register", body);
}

export function logout(): Promise<Result<{ ok?: boolean }>> {
  return request("POST", "/api/common/auth/logout");
}

export function fetchProfile(): Promise<Result<PortalProfileData>> {
  return request<PortalProfileData>("GET", "/api/common/auth/profile");
}

export function updateProfile(body: PortalNicknameBody): Promise<Result<{ ok?: boolean }>> {
  return request("PUT", "/api/common/auth/profile", body);
}

export function changePassword(body: PortalPasswordBody): Promise<Result<{ ok?: boolean }>> {
  return request("PUT", "/api/common/auth/password", body);
}

export function usernameAvailable(username: string): Promise<Result<UsernameAvailableData>> {
  return request<UsernameAvailableData>("GET", "/api/common/auth/username-available", undefined, { username });
}
