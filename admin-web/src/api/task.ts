import { http } from './http'
import type { Step } from './step'
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
  mutexGroupKey?: string
}

export interface TaskAggregateDTO {
  task: Task
  steps?: Step[]
  filters?: TaskFilter[]
  platforms?: TaskPlatform[]
}

export function listTasks() {
  return http.get('/admin/task')
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

export function publishTask(id: number) {
  return http.post(`/admin/task/${id}/publish`)
}

export function offlineTask(id: number) {
  return http.post(`/admin/task/${id}/offline`)
}
