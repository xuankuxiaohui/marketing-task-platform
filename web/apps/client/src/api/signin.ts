import type { Result } from "@mkt/shared";
import { request } from "./http";

export type PortalActivityView = {
  activityId: number;
  code: string;
  name: string;
  startTime?: string;
  endTime?: string;
};

export type CalendarDayView = {
  date: string;
  state: string;
};

export type SigninTierView = {
  day: number;
  prizeId: number;
};

export type SigninCalendarResponse = {
  activityId: number;
  activityCode: string;
  activityName: string;
  yearMonth: string;
  consecutiveDays: number;
  catchupWindowDays: number;
  catchupDailyLimit: number;
  catchupCostPoints: number;
  pointsBalance: number;
  nextRewardDay?: number;
  nextRewardPrizeId?: number;
  nextRewardHint?: string;
  days: CalendarDayView[];
  tiers: SigninTierView[];
};

export type GrantFeedbackView = {
  day: number;
  prizeId: number;
  status: string;
  hitIdempotent: boolean;
};

export type SigninActionResponse = {
  alreadySigned: boolean;
  message?: string;
  recordId: number;
  source: string;
  consecutiveDays: number;
  rewards: GrantFeedbackView[];
};

export function fetchSigninActivities(): Promise<Result<PortalActivityView[]>> {
  return request<PortalActivityView[]>("GET", "/api/common/signin/activities");
}

export function fetchSigninCalendar(activityId: number, yearMonth?: string): Promise<Result<SigninCalendarResponse>> {
  return request<SigninCalendarResponse>("GET", `/api/common/signin/${activityId}/calendar`, undefined, {
    yearMonth,
  });
}

export function postCheckin(activityId: number): Promise<Result<SigninActionResponse>> {
  return request<SigninActionResponse>("POST", `/api/common/signin/${activityId}/checkin`);
}

export function postCatchup(activityId: number, signDate: string): Promise<Result<SigninActionResponse>> {
  return request<SigninActionResponse>("POST", `/api/common/signin/${activityId}/catchup`, { signDate });
}
