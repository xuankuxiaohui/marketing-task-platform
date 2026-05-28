import { http } from './http'

export interface SignInConfig {
  id: number
  name: string
  status: string
  periodType: string
  basePoints: number
  streakConfig?: string
  catchUpEnabled: boolean
  catchUpCost: number
  catchUpMaxDays?: number
  startTime?: string
  endTime?: string
  description?: string
}

export interface SignInResult {
  success: boolean
  recordId?: number
  streakDay?: number
  basePoints?: number
  bonusPoints?: number
  totalPoints?: number
  tierReached?: number
  pointBalance?: number
  catchUp?: boolean
  message?: string
}

export interface CalendarDayEntry {
  date: string
  signed: boolean
  streakDay: number
  points: number
  catchUp: boolean
}

export interface SignInCalendarVO {
  periodKey: string
  currentStreak: number
  totalSignedDays: number
  days: CalendarDayEntry[]
}

export interface SignInStatusVO {
  todaySigned: boolean
  currentStreak: number
  totalSignedDays: number
  pointBalance: number
  nextTierDay?: number
  nextTierBonus?: number
}

export interface PointAccount {
  id: number
  userId: string
  balance: number
  totalEarned: number
  totalSpent: number
  totalExpired: number
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

export function listActiveConfigs() {
  return http.get('/client/signin/configs')
}

export function signIn(configId: number) {
  return http.post(`/client/signin/${configId}/sign`)
}

export function catchUp(configId: number, targetDate: string) {
  return http.post(`/client/signin/${configId}/catch-up`, { targetDate })
}

export function getCalendar(configId: number, periodKey: string) {
  return http.get(`/client/signin/${configId}/calendar`, { params: { periodKey } })
}

export function getStatus(configId: number) {
  return http.get(`/client/signin/${configId}/status`)
}

export function getPointBalance() {
  return http.get('/client/signin/points/balance')
}

export function getPointTransactions(params?: { page?: number; size?: number; type?: string }) {
  return http.get('/client/signin/points/transactions', { params })
}
