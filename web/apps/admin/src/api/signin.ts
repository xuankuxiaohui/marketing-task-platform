import type { Result } from "@mkt/shared";
import type { PageParams } from "./identity";
import { request } from "./http";

export type SigninTierView = {
  day: number;
  prizeId: number;
};

export type SigninActivityView = {
  id: number;
  code: string;
  name: string;
  startTime: string;
  endTime: string;
  status: string;
  version: number;
  pendingRevision: boolean;
  schedulePublishAt?: string;
  tiers: SigninTierView[];
};

export type SigninActivitySaveCommand = {
  id?: number;
  code: string;
  name: string;
  startTime: string;
  endTime: string;
  tiers: SigninTierView[];
};

export type SigninActivitySaveResponse = {
  id: number;
  code: string;
  version: number;
  status: string;
};

export type SigninPublishResponse = {
  requiresConfirm: boolean;
  message?: string;
  id: number;
  code: string;
  version: number;
  status: string;
};

export type SigninRecordView = {
  id: number;
  activityId: number;
  userId: number;
  signDate: string;
  source: string;
  createdAt?: string;
};

export type PageDataSigninActivityView = {
  total: number;
  records: SigninActivityView[];
};

export type PageDataSigninRecordView = {
  total: number;
  records: SigninRecordView[];
};

export type OkResponse = { ok: boolean };

export function pageSigninActivities(
  params: PageParams & { code?: string; name?: string; status?: string },
): Promise<Result<PageDataSigninActivityView>> {
  return request("GET", "/admin/signin/activities", undefined, params);
}

export function getSigninActivity(id: number): Promise<Result<SigninActivityView>> {
  return request("GET", `/admin/signin/activities/${id}`);
}

export function saveSigninActivity(body: SigninActivitySaveCommand): Promise<Result<SigninActivitySaveResponse>> {
  return request("POST", "/admin/signin/activities", body);
}

export function deleteSigninActivity(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/signin/activities/${id}`);
}

export function publishSigninActivity(
  id: number,
  body?: { confirm?: boolean; early?: boolean },
): Promise<Result<SigninPublishResponse>> {
  return request("POST", `/admin/signin/activities/${id}/publish`, body ?? {});
}

export function scheduleSigninActivity(id: number, body: { publishAt: string }): Promise<Result<SigninPublishResponse>> {
  return request("POST", `/admin/signin/activities/${id}/schedule`, body);
}

export function offlineSigninActivity(id: number): Promise<Result<SigninPublishResponse>> {
  return request("POST", `/admin/signin/activities/${id}/offline`);
}

export function pageSigninRecords(
  params: PageParams & { activityId?: number; userId?: number; from?: string; to?: string },
): Promise<Result<PageDataSigninRecordView>> {
  return request("GET", "/admin/signin/records", undefined, params);
}
