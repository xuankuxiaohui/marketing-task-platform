import { http } from './http'

export interface PrizeRecord {
  id: number
  userId: string
  prizeType: string
  prizeName: string
  prizeIcon?: string
  prizeImage?: string
  quantity?: number
  status: string
  expireTime?: string
  errorMessage?: string
  externalTradeNo?: string
  wonAt?: string
  claimedAt?: string
  grantedAt?: string
}

export interface PrizeRecordsData {
  records: PrizeRecord[]
  counts: Record<string, number>
}

export interface ClaimResult {
  status: string
  tradeNo?: string
  errorMessage?: string
}

export function getPrizeRecords(status?: string) {
  return http.get('/client/prize/records', { params: status ? { status } : {} })
}

export function getPrizeRecordDetail(recordId: number) {
  return http.get(`/client/prize/${recordId}`)
}

export function claimPrize(recordId: number) {
  return http.post(`/client/prize/${recordId}/claim`)
}
