import { http } from './http'

export interface Activity {
  id?: number
  code: string
  name: string
  description?: string
  status?: string
  grayType?: string
  grayConfig?: string
  startTime?: string
  endTime?: string
  participationRules?: string
  cacheVersion?: number
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface ActivityDisplayRule {
  activityCode: string
  content: string
  contentHash?: string
  updatedAt?: string
  updatedBy?: string
}

export interface ActivitySubModules {
  tasks: { id: number; name: string; status: string }[]
  signInConfigs: { id: number; name: string; status: string }[]
  prizes: { id: number; name: string; type: string }[]
}

export interface ActivityListParams {
  page?: number
  size?: number
  status?: string
}

export function listActivities(params?: ActivityListParams) {
  return http.get('/admin/activities', { params })
}

export function getActivity(id: number) {
  return http.get(`/admin/activities/${id}`)
}

export function createActivity(activity: Partial<Activity>) {
  return http.post('/admin/activities', activity)
}

export function updateActivity(id: number, activity: Partial<Activity>) {
  return http.put(`/admin/activities/${id}`, activity)
}

export function deleteActivity(id: number) {
  return http.delete(`/admin/activities/${id}`)
}

export function publishActivity(id: number) {
  return http.post(`/admin/activities/${id}/publish`)
}

export function offlineActivity(id: number) {
  return http.post(`/admin/activities/${id}/offline`)
}

export function backToDraftActivity(id: number) {
  return http.post(`/admin/activities/${id}/back-to-draft`)
}

export function getDisplayRule(id: number) {
  return http.get(`/admin/activities/${id}/display-rule`)
}

export function updateDisplayRule(id: number, content: string) {
  return http.put(`/admin/activities/${id}/display-rule`, { content })
}

export function getSubModules(id: number) {
  return http.get(`/admin/activities/${id}/sub-modules`)
}
