import type { Result } from "@mkt/shared";
import { request } from "./http";

export type PortalActivityView = {
  id: number;
  code: string;
  name: string;
  startTime?: string;
  endTime?: string;
};

export type SubmoduleView = {
  type: string;
  refId: number;
  sort: number;
};

export type PortalActivityDetailView = {
  id: number;
  code: string;
  name: string;
  startTime?: string;
  endTime?: string;
  richText: string;
  contentHash: string;
  version: number;
  submodules: SubmoduleView[];
};

export type ParticipateResponse = {
  participationId: number;
  result: string;
  granted: boolean;
  grantRecordId?: number | null;
};

export function fetchActivities(): Promise<Result<PortalActivityView[]>> {
  return request<PortalActivityView[]>("GET", "/api/common/activity/activities");
}

export function fetchActivityDetail(activityId: number): Promise<Result<PortalActivityDetailView>> {
  return request<PortalActivityDetailView>("GET", `/api/common/activity/${activityId}`);
}

export function postParticipate(activityId: number): Promise<Result<ParticipateResponse>> {
  return request<ParticipateResponse>("POST", `/api/common/activity/${activityId}/participate`);
}
