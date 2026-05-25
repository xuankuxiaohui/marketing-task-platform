import { http } from './http'

export interface OperationLogVO {
  id: number
  operatorId: string
  operatorName: string
  operationType: string
  targetType: string
  targetId: number
  targetName: string
  detail: string
  createdAt: string
}

export interface OperationLogQueryParams {
  page?: number
  size?: number
  operationType?: string
  targetType?: string
  operatorId?: string
  startDate?: string
  endDate?: string
}

export function listOperationLogs(params: OperationLogQueryParams = {}) {
  return http.get('/admin/operation-logs', { params })
}
