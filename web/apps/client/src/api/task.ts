import type { components, paths } from "@mkt/shared/openapi/portal";
import type { Result } from "@mkt/shared";
import { request } from "./http";

type Star<T> = T extends { content: { "*/*": infer B } } ? B : never;
type Envelope<T> = T extends { data?: infer D } ? D : unknown;

export type MineTaskResult = Star<paths["/api/common/task/mine"]["get"]["responses"][200]>;
export type MineTaskPage = NonNullable<Envelope<MineTaskResult>>;
export type MineTaskView = components["schemas"]["MineTaskView"];
export type TaskListResult = Star<paths["/api/common/task/list"]["get"]["responses"][200]>;
export type TaskListPage = NonNullable<Envelope<TaskListResult>>;
export type TaskCardView = components["schemas"]["TaskCardView"];
export type TaskDetailResult = Star<paths["/api/common/task/{taskId}/detail"]["get"]["responses"][200]>;
export type TaskDetailView = NonNullable<Envelope<TaskDetailResult>>;
export type TaskStartResult = Star<paths["/api/common/task/{taskId}/start"]["post"]["responses"][200]>;
export type TaskStartView = NonNullable<Envelope<TaskStartResult>>;
export type TaskClickResult = Star<
  paths["/api/common/task/instances/{instanceId}/steps/{stepCode}/click"]["post"]["responses"][200]
>;
export type TaskClickView = NonNullable<Envelope<TaskClickResult>>;
export type TaskAbandonResult = Star<
  paths["/api/common/task/instances/{instanceId}/abandon"]["post"]["responses"][200]
>;
export type TaskAbandonView = NonNullable<Envelope<TaskAbandonResult>>;
export type RewardPreviewView = components["schemas"]["RewardPreviewView"];
export type InstanceStepView = components["schemas"]["InstanceStepView"];
export type CurrentStepView = components["schemas"]["CurrentStepView"];
export type RewardFeedbackView = components["schemas"]["RewardFeedbackView"];
export type PlatformActionView = components["schemas"]["PlatformActionView"];

export const INSTANCE_FROZEN_CODE = "task.instance.frozen";

export type TaskPageQuery = {
  category?: string;
  status?: string;
  page?: number;
  pageSize?: number;
};

export function fetchTaskList(query: TaskPageQuery = {}): Promise<Result<TaskListPage>> {
  return request<TaskListPage>("GET", "/api/common/task/list", undefined, {
    category: query.category,
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  });
}

export function fetchMineTasks(query: TaskPageQuery = {}): Promise<Result<MineTaskPage>> {
  return request<MineTaskPage>("GET", "/api/common/task/mine", undefined, {
    status: query.status,
    category: query.category,
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  });
}

export function fetchTaskDetail(taskId: number): Promise<Result<TaskDetailView>> {
  return request<TaskDetailView>("GET", `/api/common/task/${taskId}/detail`);
}

export function startTask(taskId: number): Promise<Result<TaskStartView>> {
  return request<TaskStartView>("POST", `/api/common/task/${taskId}/start`);
}

export function clickTaskStep(instanceId: number, stepCode: string): Promise<Result<TaskClickView>> {
  return request<TaskClickView>("POST", `/api/common/task/instances/${instanceId}/steps/${stepCode}/click`);
}

export function abandonTask(instanceId: number): Promise<Result<TaskAbandonView>> {
  return request<TaskAbandonView>("POST", `/api/common/task/instances/${instanceId}/abandon`);
}
