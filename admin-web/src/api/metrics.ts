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

export interface ActivityStats {
  activityCode: string
  activityName?: string
  participantCount: number
  completionCount: number
  rewardCount: number
}

export interface ActivityDailyStats {
  activityCode: string
  statDate: string
  participantCount: number
  completionCount: number
  rewardCount: number
}

export function getActivityOverview() {
  return http.get('/admin/metrics/activities')
}

export function getActivitySummary(activityCode: string) {
  return http.get(`/admin/metrics/activity/${activityCode}/summary`)
}

export function getActivityDaily(activityCode: string, from?: string, to?: string) {
  return http.get(`/admin/metrics/activity/${activityCode}/daily`, { params: { from, to } })
}
