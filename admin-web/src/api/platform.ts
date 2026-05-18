import { http } from './http'

export interface TaskPlatform {
  id?: number
  taskId?: number
  platform: string
  flowDesc?: string
  buttonText?: string
  jumpUri?: string
  enabled?: boolean
}

export function listPlatforms(taskId: number) {
  return http.get(`/admin/task/${taskId}/platforms`)
}

export function upsertPlatform(taskId: number, platform: TaskPlatform) {
  return http.post(`/admin/task/${taskId}/platforms`, platform)
}

export function deletePlatform(taskId: number, platformId: number) {
  return http.delete(`/admin/task/${taskId}/platforms/${platformId}`)
}
