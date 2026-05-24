import { http } from './http'

export interface TaskMetrics {
  id: number
  taskId: number
  metricDate: string
  views: number
  participants: number
  completions: number
  rewardSuccess: number
  rewardFailure: number
  avgFilterMs: number
}

export function getDashboard() {
  return http.get('/admin/metrics/dashboard')
}

export function getTaskSummary(taskId: number) {
  return http.get(`/admin/metrics/task/${taskId}/summary`)
}

export function getTaskDaily(taskId: number, from: string, to: string) {
  return http.get(`/admin/metrics/task/${taskId}/daily`, { params: { from, to } })
}
