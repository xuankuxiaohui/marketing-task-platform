import type { Result } from "@mkt/shared";
import { request } from "./http";

export type SimulateTaskCard = {
  taskId: number;
  taskCode: string;
  name: string;
  category?: string;
  userStatus?: string;
};

export type PageDataSimulateTaskCard = { total: number; records: SimulateTaskCard[] };

export type SimulateStartView = {
  instanceId: number;
  instanceStatus: string;
};

export type SimulateClickView = {
  instanceId: number;
  stepStatus: string;
  instanceStatus: string;
};

export type SimulateCallbackView = {
  instanceId: number;
  stepCode: string;
  stepStatus: string;
  instanceStatus: string;
};

export type SimulateProgressView = {
  instanceId: number;
  stepCode: string;
  progressCurrent: number;
  progressTarget?: number | null;
  stepStatus: string;
};

export type SimulateFlowStepView = {
  stepCode: string;
  type: string;
  action: string;
  stepStatus: string;
};

export type SimulateFlowView = {
  instanceId: number;
  instanceStatus: string;
  steps: SimulateFlowStepView[];
  grantRecordIds: number[];
};

export type SimulateReverseView = {
  instanceId: number;
  pointsReversed: number;
  stockRestored: number;
  sendingMarked: number;
  channelRevoked: boolean;
};

export type SimulateDetailView = {
  status: string;
  instanceId?: number | null;
};

export function simulateList(query: {
  userId: number;
  category?: string;
  page?: number;
  pageSize?: number;
}): Promise<Result<PageDataSimulateTaskCard>> {
  return request("GET", "/admin/simulate/task/list", undefined, query);
}

export function simulateDetail(userId: number, taskId: number): Promise<Result<SimulateDetailView>> {
  return request("GET", "/admin/simulate/task/detail", undefined, { userId, taskId });
}

export function simulateStart(userId: number, taskId: number): Promise<Result<SimulateStartView>> {
  return request("POST", "/admin/simulate/task/start", { userId, taskId });
}

export function simulateClick(userId: number, instanceId: number, stepCode: string): Promise<Result<SimulateClickView>> {
  return request("POST", "/admin/simulate/task/click", { userId, instanceId, stepCode });
}

export function simulateCallback(
  userId: number,
  instanceId: number,
  stepCode: string,
  bizNo?: string,
): Promise<Result<SimulateCallbackView>> {
  return request("POST", "/admin/simulate/task/callback", { userId, instanceId, stepCode, bizNo });
}

export function simulateProgress(
  userId: number,
  instanceId: number,
  stepCode: string,
  value: number,
  reportId: string,
): Promise<Result<SimulateProgressView>> {
  return request("POST", "/admin/simulate/task/progress", { userId, instanceId, stepCode, value, reportId });
}

export function simulateFlow(userId: number, taskId: number): Promise<Result<SimulateFlowView>> {
  return request("POST", "/admin/simulate/task/flow", { userId, taskId });
}

export function simulateReverse(instanceId: number): Promise<Result<SimulateReverseView>> {
  return request("POST", "/admin/simulate/task/reverse", { instanceId });
}
