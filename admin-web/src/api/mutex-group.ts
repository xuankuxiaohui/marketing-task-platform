import { http } from './http'

export interface MutexGroup {
  id?: number
  name: string
  description?: string
  scope: string
  crossCycle?: boolean
  taskCount?: number
  createdAt?: string
}

export function listMutexGroups() {
  return http.get('/admin/mutex-groups')
}

export function getMutexGroup(id: number) {
  return http.get(`/admin/mutex-groups/${id}`)
}

export function getMutexGroupTasks(id: number) {
  return http.get(`/admin/mutex-groups/${id}/tasks`)
}

export function createMutexGroup(group: MutexGroup) {
  return http.post('/admin/mutex-groups', group)
}

export function updateMutexGroup(id: number, group: MutexGroup) {
  return http.put(`/admin/mutex-groups/${id}`, group)
}

export function deleteMutexGroup(id: number) {
  return http.delete(`/admin/mutex-groups/${id}`)
}

export function unlinkMutexGroupTask(groupId: number, taskId: number) {
  return http.delete(`/admin/mutex-groups/${groupId}/tasks/${taskId}`)
}
