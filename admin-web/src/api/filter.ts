import { http } from './http'

export interface TaskFilter {
  id?: number
  taskId?: number
  seq: number
  expression: string
  logicOp?: string
  description?: string
  enabled?: boolean
}

export function validateFilter(expression: string) {
  return http.post('/admin/filter/validate', { expression })
}

export function listFilters(taskId: number) {
  return http.get(`/admin/task/${taskId}/filters`)
}

export function createFilter(taskId: number, filter: TaskFilter) {
  return http.post(`/admin/task/${taskId}/filters`, filter)
}

export function updateFilter(taskId: number, filterId: number, filter: TaskFilter) {
  return http.put(`/admin/task/${taskId}/filters/${filterId}`, filter)
}

export function deleteFilter(taskId: number, filterId: number) {
  return http.delete(`/admin/task/${taskId}/filters/${filterId}`)
}
