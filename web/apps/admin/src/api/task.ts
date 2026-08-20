import type { components } from "@mkt/shared/openapi/admin";
import type { Result } from "@mkt/shared";
import type { PageParams } from "./identity";
import { request } from "./http";

export type TaskDefinitionView = components["schemas"]["TaskDefinitionView"];
export type PageDataTaskDefinitionView = components["schemas"]["PageDataTaskDefinitionView"];
export type TaskDefinitionAggregateResponse = components["schemas"]["TaskDefinitionAggregateResponse"];
export type TaskDefinitionSaveCommand = components["schemas"]["TaskDefinitionSaveCommand"];
export type TaskDefinitionSaveResponse = components["schemas"]["TaskDefinitionSaveResponse"];
export type TaskStepCommand = components["schemas"]["TaskStepCommand"];
export type TaskTransitionCommand = components["schemas"]["TaskTransitionCommand"];
export type TaskActionCommand = components["schemas"]["TaskActionCommand"];
export type TaskGrayCommand = components["schemas"]["TaskGrayCommand"];
export type TaskFilterCommand = components["schemas"]["TaskFilterCommand"];
export type PublishCommand = components["schemas"]["PublishCommand"];
export type PublishResponse = components["schemas"]["PublishResponse"];
export type ScheduleCommand = components["schemas"]["ScheduleCommand"];
export type TaskCopyCommand = components["schemas"]["TaskCopyCommand"];
export type TaskVersionView = components["schemas"]["TaskVersionView"];
export type VersionDiffResponse = components["schemas"]["VersionDiffResponse"];
export type ScheduleFailureView = components["schemas"]["ScheduleFailureView"];
export type PageDataScheduleFailureView = components["schemas"]["PageDataScheduleFailureView"];
export type ExpressionValidateCommand = components["schemas"]["ExpressionValidateCommand"];
export type ExpressionValidateResponse = components["schemas"]["ExpressionValidateResponse"];
export type MutexGroupResponse = components["schemas"]["MutexGroupResponse"];
export type MutexGroupSaveCommand = components["schemas"]["MutexGroupSaveCommand"];
export type PageDataMutexGroupResponse = components["schemas"]["PageDataMutexGroupResponse"];
export type CrowdResponse = components["schemas"]["CrowdResponse"];
export type CrowdSaveCommand = components["schemas"]["CrowdSaveCommand"];
export type CrowdImportCommand = components["schemas"]["CrowdImportCommand"];
export type CrowdImportResponse = components["schemas"]["CrowdImportResponse"];
export type PageDataCrowdResponse = components["schemas"]["PageDataCrowdResponse"];
export type AdminInstanceView = components["schemas"]["AdminInstanceView"];
export type AdminInstanceDetailResponse = components["schemas"]["AdminInstanceDetailResponse"];
export type InstanceAbandonCommand = components["schemas"]["InstanceAbandonCommand"];
export type InstanceAbandonResponse = components["schemas"]["InstanceAbandonResponse"];
export type PageDataAdminInstanceView = components["schemas"]["PageDataAdminInstanceView"];
export type BatchIdsCommand = components["schemas"]["BatchIdsCommand"];
export type BatchItemResponse = components["schemas"]["BatchItemResponse"];
export type IdResponse = components["schemas"]["IdResponse"];
export type OkResponse = components["schemas"]["OkResponse"];

export function pageDefinitions(
  params: PageParams & { code?: string; name?: string; status?: string; category?: string },
): Promise<Result<PageDataTaskDefinitionView>> {
  return request("GET", "/admin/task/definitions", undefined, params);
}

export function getDefinition(id: number): Promise<Result<TaskDefinitionAggregateResponse>> {
  return request("GET", `/admin/task/definitions/${id}`);
}

export function saveDefinition(body: TaskDefinitionSaveCommand): Promise<Result<TaskDefinitionSaveResponse>> {
  return request("POST", "/admin/task/definitions/save-aggregate", body);
}

export function copyDefinition(id: number, body: TaskCopyCommand): Promise<Result<TaskDefinitionSaveResponse>> {
  return request("POST", `/admin/task/definitions/${id}/copy`, body);
}

export function deleteDefinition(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/task/definitions/${id}`);
}

export function publishDefinition(id: number, body?: PublishCommand): Promise<Result<PublishResponse>> {
  return request("POST", `/admin/task/definitions/${id}/publish`, body ?? {});
}

export function scheduleDefinition(id: number, body: ScheduleCommand): Promise<Result<PublishResponse>> {
  return request("POST", `/admin/task/definitions/${id}/schedule`, body);
}

export function cancelSchedule(id: number): Promise<Result<PublishResponse>> {
  return request("POST", `/admin/task/definitions/${id}/cancel-schedule`);
}

export function offlineDefinition(id: number): Promise<Result<PublishResponse>> {
  return request("POST", `/admin/task/definitions/${id}/offline`);
}

export function resetRevision(id: number): Promise<Result<PublishResponse>> {
  return request("POST", `/admin/task/definitions/${id}/reset-revision`);
}

export function batchPublish(ids: number[]): Promise<Result<BatchItemResponse[]>> {
  return request("POST", "/admin/task/definitions/batch-publish", { ids } satisfies BatchIdsCommand);
}

export function batchOffline(ids: number[]): Promise<Result<BatchItemResponse[]>> {
  return request("POST", "/admin/task/definitions/batch-offline", { ids } satisfies BatchIdsCommand);
}

export function listVersions(id: number): Promise<Result<TaskVersionView[]>> {
  return request("GET", `/admin/task/definitions/${id}/versions`);
}

export function diffVersions(id: number, left: number, right: number): Promise<Result<VersionDiffResponse>> {
  return request("GET", `/admin/task/definitions/${id}/versions/diff`, undefined, { left, right });
}

export function pageScheduleFailures(params: PageParams): Promise<Result<PageDataScheduleFailureView>> {
  return request("GET", "/admin/task/definitions/schedule-failures", undefined, params);
}

export function validateExpression(body: ExpressionValidateCommand): Promise<Result<ExpressionValidateResponse>> {
  return request("POST", "/admin/task/expressions/validate", body);
}

export function pageMutexGroups(params: PageParams): Promise<Result<PageDataMutexGroupResponse>> {
  return request("GET", "/admin/task/mutex-groups", undefined, params);
}

export function createMutexGroup(body: MutexGroupSaveCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/task/mutex-groups", body);
}

export function updateMutexGroup(id: number, body: MutexGroupSaveCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/task/mutex-groups/${id}`, body);
}

export function deleteMutexGroup(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/task/mutex-groups/${id}`);
}

export function pageCrowds(params: PageParams): Promise<Result<PageDataCrowdResponse>> {
  return request("GET", "/admin/task/crowds", undefined, params);
}

export function createCrowd(body: CrowdSaveCommand): Promise<Result<IdResponse>> {
  return request("POST", "/admin/task/crowds", body);
}

export function updateCrowd(id: number, body: CrowdSaveCommand): Promise<Result<OkResponse>> {
  return request("PUT", `/admin/task/crowds/${id}`, body);
}

export function deleteCrowd(id: number): Promise<Result<OkResponse>> {
  return request("DELETE", `/admin/task/crowds/${id}`);
}

export function importCrowd(id: number, body: CrowdImportCommand): Promise<Result<CrowdImportResponse>> {
  return request("POST", `/admin/task/crowds/${id}/import`, body);
}

export function pageInstances(
  params: PageParams & { taskId?: number; userId?: number; status?: string; simulated?: number; from?: string; to?: string },
): Promise<Result<PageDataAdminInstanceView>> {
  return request("GET", "/admin/task/instances", undefined, params);
}

export function getInstance(id: number): Promise<Result<AdminInstanceDetailResponse>> {
  return request("GET", `/admin/task/instances/${id}`);
}

export function abandonInstance(id: number, body: InstanceAbandonCommand): Promise<Result<InstanceAbandonResponse>> {
  return request("POST", `/admin/task/instances/${id}/abandon`, body);
}
