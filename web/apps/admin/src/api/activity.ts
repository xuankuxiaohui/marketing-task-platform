import type { Result } from "@mkt/shared";
import type { PageParams } from "./identity";
import { request } from "./http";

export type ActivityGrayView = {
  type: string;
  ratio?: number | null;
};

export type SubmoduleView = {
  type: string;
  refId: number;
  sort: number;
};

export type ActivityView = {
  id: number;
  code: string;
  name: string;
  startTime: string;
  endTime: string;
  status: string;
  version: number;
  pendingRevision: boolean;
  schedulePublishAt?: string;
  scheduleOfflineAt?: string;
  richText?: string;
  contentHash?: string;
  gray?: ActivityGrayView;
  submodules?: SubmoduleView[];
  participationPrizeId?: number | null;
  allowUserIds?: number[];
  allowCrowdCodes?: string[];
  newUserOnly: boolean;
  newUserDays: number;
  userDailyLimit?: number | null;
  userTotalLimit?: number | null;
  globalDailyLimit?: number | null;
  regions?: string[];
};

export type ActivitySaveCommand = {
  id?: number;
  code: string;
  name: string;
  startTime: string;
  endTime: string;
  richText: string;
  gray?: { type: string; ratio?: number | null };
  submodules?: { type: string; refId: number; sort?: number }[];
  participationPrizeId?: number | null;
  allowUserIds?: number[];
  allowCrowdCodes?: string[];
  newUserOnly?: boolean;
  newUserDays?: number;
  userDailyLimit?: number | null;
  userTotalLimit?: number | null;
  globalDailyLimit?: number | null;
  regions?: string[];
};

export type ActivitySaveResponse = {
  id: number;
  code: string;
  version: number;
  status: string;
};

export type ActivityPublishResponse = {
  requiresConfirm: boolean;
  message?: string;
  id: number;
  code: string;
  version: number;
  status: string;
};

export type ParticipationView = {
  id: number;
  activityId: number;
  userId: number;
  periodKey: string;
  result: string;
  hitRule?: string;
  createdAt?: string;
};

export type ParticipationStatsView = {
  total: number;
  passCount: number;
  rejectCount: number;
  passRate: number;
  rejectReasons: { hitRule: string; count: number }[];
};

export type PageDataActivityView = { total: number; records: ActivityView[] };
export type PageDataParticipationView = { total: number; records: ParticipationView[] };
export type OkResponse = { ok: boolean };

export function pageActivities(
  params: PageParams & { code?: string; name?: string; status?: string },
): Promise<Result<PageDataActivityView>> {
  return request("GET", "/admin/activity/activities", undefined, params);
}

export function getActivity(id: number): Promise<Result<ActivityView>> {
  return request("GET", `/admin/activity/activities/${id}`);
}

export function saveActivity(body: ActivitySaveCommand): Promise<Result<ActivitySaveResponse>> {
  return request("POST", "/admin/activity/activities", body);
}

export function deleteActivity(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/activity/activities/${id}`);
}

export function publishActivity(
  id: number,
  body?: { confirm?: boolean; early?: boolean },
): Promise<Result<ActivityPublishResponse>> {
  return request("POST", `/admin/activity/activities/${id}/publish`, body ?? {});
}

export function scheduleActivity(
  id: number,
  body: { publishAt?: string; offlineAt?: string },
): Promise<Result<ActivityPublishResponse>> {
  return request("POST", `/admin/activity/activities/${id}/schedule`, body);
}

export function offlineActivity(id: number): Promise<Result<ActivityPublishResponse>> {
  return request("POST", `/admin/activity/activities/${id}/offline`);
}

export function pageParticipations(
  params: PageParams & { activityId?: number; userId?: number; result?: string; periodKey?: string },
): Promise<Result<PageDataParticipationView>> {
  return request("GET", "/admin/activity/participations", undefined, params);
}

export function activityStats(id: number): Promise<Result<ParticipationStatsView>> {
  return request("GET", `/admin/activity/activities/${id}/stats`);
}
