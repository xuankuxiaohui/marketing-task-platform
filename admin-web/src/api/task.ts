import { http } from './http'

export interface Task {
  id?: number
  code: string
  name: string
  description?: string
  periodType: string
  status?: string
  version?: number
}

export function listTasks() {
  return http.get('/admin/task')
}

export function saveTask(task: Task) {
  return http.post('/admin/task', task)
}

export function publishTask(id: number) {
  return http.post(`/admin/task/${id}/publish`)
}

export function offlineTask(id: number) {
  return http.post(`/admin/task/${id}/offline`)
}
