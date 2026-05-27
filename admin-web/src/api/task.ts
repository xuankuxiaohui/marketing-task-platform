import { http } from './http'
import type { Step, StepTransition } from './step'
import type { TaskFilter } from './filter'
import type { TaskPlatform } from './platform'

export interface Task {
  id?: number
  code: string
  name: string
  description?: string
  periodType: string
  status?: string
  version?: number
  mutexGroupId?: number
  mutexGroupName?: string
  grayType?: string
  grayConfig?: string
  createdAt?: string
  updatedAt?: string
  stepCount?: number
  instanceCount?: number
}

export interface TaskListParams {
  page?: number
  size?: number
  status?: string
  keyword?: string
  periodType?: string
}

export interface TaskAggregateDTO {
  task: Task
  steps?: Step[]
  filters?: TaskFilter[]
  platforms?: TaskPlatform[]
  stepPlatforms?: any[]
  transitions?: StepTransition[]
}

export function listTasks(params?: TaskListParams) {
  return http.get('/admin/task', { params })
}

export function saveTask(task: Task) {
  return http.post('/admin/task', task)
}

export function saveTaskAggregate(dto: TaskAggregateDTO) {
  return http.post('/admin/task', dto)
}

export function getTaskById(id: number) {
  return http.get(`/admin/task/${id}`)
}

export function deleteTask(id: number) {
  return http.delete(`/admin/task/${id}`)
}

export function publishTask(id: number) {
  return http.post(`/admin/task/${id}/publish`)
}

export function offlineTask(id: number) {
  return http.post(`/admin/task/${id}/offline`)
}

export function copyTask(id: number, data?: { name?: string; code?: string }) {
  return http.post(`/admin/task/${id}/copy`, data)
}

export interface BatchTaskResult {
  success: number[]
  failed: { id: number; reason: string }[]
}

export function batchPublishTasks(taskIds: number[]) {
  return http.post<BatchTaskResult>('/admin/task/batch-publish', { taskIds })
}

export function batchOfflineTasks(taskIds: number[]) {
  return http.post<BatchTaskResult>('/admin/task/batch-offline', { taskIds })
}

export function schedulePublishTask(id: number, publishAt: string) {
  return http.post(`/admin/task/${id}/schedule-publish`, { publishAt })
}

export function cancelSchedulePublish(id: number) {
  return http.post(`/admin/task/${id}/cancel-schedule`)
}

export interface TaskVersion {
  id: number
  taskId: number
  version: number
  createdAt: string
}

export interface TaskVersionDetail {
  id: number
  taskId: number
  version: number
  snapshotJson: string
  createdAt: string
}

export function getTaskVersions(id: number) {
  return http.get(`/admin/task/${id}/versions`)
}

export function getTaskVersionDetail(id: number, versionId: number) {
  return http.get(`/admin/task/${id}/versions/${versionId}`)
}
