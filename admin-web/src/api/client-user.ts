import { http } from './http'

export interface ClientUserVO {
  id: number
  username: string
  nickname: string | null
  province: string | null
  role: string | null
  tags: string | null
  orgId: string | null
  level: number | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ClientUserQueryParams {
  page?: number
  size?: number
  keyword?: string
}

export function listClientUsers(params: ClientUserQueryParams = {}) {
  return http.get('/admin/client-users', { params })
}

export function resetClientUserPassword(id: number) {
  return http.put(`/admin/client-users/${id}/reset-password`)
}

export function toggleClientUserEnabled(id: number) {
  return http.put(`/admin/client-users/${id}/toggle-enabled`)
}

export function kickClientUser(id: number) {
  return http.post(`/admin/client-users/${id}/kick`)
}
