import { http } from './http'

export interface AdminUserVO {
  id: number
  username: string
  nickname: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface AdminUserQueryParams {
  page?: number
  size?: number
  keyword?: string
}

export function listAdminUsers(params: AdminUserQueryParams = {}) {
  return http.get('/admin/admin-users', { params })
}

export function resetAdminUserPassword(id: number) {
  return http.put(`/admin/admin-users/${id}/reset-password`)
}

export function toggleAdminUserEnabled(id: number) {
  return http.put(`/admin/admin-users/${id}/toggle-enabled`)
}

export function kickAdminUser(id: number) {
  return http.post(`/admin/admin-users/${id}/kick`)
}
