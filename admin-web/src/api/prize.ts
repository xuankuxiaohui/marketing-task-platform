import { http } from './http'

export interface Prize {
  id?: number
  type: string
  name: string
  description?: string
  handlerBean?: string
  paramsJson?: string
  totalStock?: number
  monthlyStock?: number
  dailyStock?: number
  userTotalLimit?: number
  userMonthlyLimit?: number
  userDailyLimit?: number
  limitsJson?: string
  activityId?: number
  groupKey?: string
  groupStrategy?: string
  groupWeight?: number
  iconUrl?: string
  claimZoneImageUrl?: string
  autoGrant?: boolean
  claimExpireType?: string
  claimExpireValue?: string
  maxRetry?: number
  enabled?: boolean
  startTime?: string
  endTime?: string
  createdAt?: string
  updatedAt?: string
}

export interface PrizeRecord {
  id?: number
  userId: string
  instanceId?: number
  taskId?: number
  stepId?: number
  prizeId?: number
  quantity?: number
  prizeType?: string
  prizeName?: string
  prizeIcon?: string
  prizeImage?: string
  status?: string
  expireTime?: string
  retryCount?: number
  errorMessage?: string
  externalTradeNo?: string
  wonAt?: string
  claimedAt?: string
  grantedAt?: string
}

export function listPrizes(page: number = 1, size: number = 20) {
  return http.get('/admin/prize', { params: { page, size } })
}

export function getPrize(id: number) {
  return http.get(`/admin/prize/${id}`)
}

export function createPrize(prize: Prize) {
  return http.post('/admin/prize', prize)
}

export function updatePrize(id: number, prize: Prize) {
  return http.put(`/admin/prize/${id}`, prize)
}

export function togglePrize(id: number) {
  return http.post(`/admin/prize/${id}/toggle`)
}

export function getPrizeRecords(id: number) {
  return http.get(`/admin/prize/${id}/records`)
}
