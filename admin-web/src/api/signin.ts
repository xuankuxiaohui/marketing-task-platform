import { http } from './http'

export interface SignInConfig {
  id?: number
  name: string
  activityCode?: string
  status?: string
  periodType: string
  basePoints: number
  streakConfig?: string
  pointExpireDays?: number
  catchUpEnabled: boolean
  catchUpCost: number
  catchUpMaxDays?: number
  startTime?: string
  endTime?: string
  description?: string
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface StreakTier {
  day: number
  bonus: number
}

export interface StreakConfigObj {
  maxStreak: number
  tiers: StreakTier[]
}

export interface SignInRecord {
  id: number
  configId: number
  userId: string
  signinDate: string
  periodKey: string
  streakDay: number
  basePoints: number
  bonusPoints: number
  totalPoints: number
  tierReached?: number
  catchUp: boolean
  createdAt?: string
}

export interface PointTransaction {
  id: number
  userId: string
  type: string
  amount: number
  sourceType: string
  sourceId?: number
  balanceAfter: number
  expireAt?: string
  status: string
  description?: string
  createdAt?: string
}

export interface ConfigStats {
  todaySigned: number
  totalSigned: number
}

export interface GrantPointsBody {
  userId: string
  amount: number
  description?: string
}

export interface SignInConfigQuery {
  page?: number
  size?: number
  status?: string
  keyword?: string
}

export interface PointTransactionQuery {
  page?: number
  size?: number
  userId?: string
  type?: string
}

export function listSignInConfigs(params: SignInConfigQuery) {
  return http.get('/admin/signin/configs', { params })
}

export function getSignInConfig(id: number) {
  return http.get(`/admin/signin/configs/${id}`)
}

export function createSignInConfig(config: SignInConfig) {
  return http.post('/admin/signin/configs', config)
}

export function updateSignInConfig(id: number, config: SignInConfig) {
  return http.put(`/admin/signin/configs/${id}`, config)
}

export function publishSignInConfig(id: number) {
  return http.post(`/admin/signin/configs/${id}/publish`)
}

export function offlineSignInConfig(id: number) {
  return http.post(`/admin/signin/configs/${id}/offline`)
}

export function deleteSignInConfig(id: number) {
  return http.delete(`/admin/signin/configs/${id}`)
}

export function getSignInConfigStats(id: number) {
  return http.get(`/admin/signin/configs/${id}/stats`)
}

export function getSignInRecords(configId: number, params?: { page?: number; size?: number; userId?: string }) {
  return http.get(`/admin/signin/configs/${configId}/records`, { params })
}

export function listPointTransactions(params: PointTransactionQuery) {
  return http.get('/admin/signin/points/transactions', { params })
}

export function grantPoints(body: GrantPointsBody) {
  return http.post('/admin/signin/points/grant', body)
}
