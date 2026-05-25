import { http } from './http'

export interface InstanceVO {
  id: number
  userId: string
  taskId: number
  taskVersion: number
  cycleKey: string
  status: string
  currentStepSeq: number
  startTime: string
  completeTime: string | null
  rewardTime: string | null
  createdAt: string
  taskName?: string
}

export interface StepProgressDetail {
  stepId: number
  stepSeq: number
  stepName: string
  stepType: string
  stepDescription: string
  targetValue: number | null
  status: string
  progressValue: number | null
  completeTime: string | null
}

export interface InstanceDetail {
  instance: InstanceVO
  steps: StepProgressDetail[]
  totalSteps: number
}

export interface InstanceQueryParams {
  page?: number
  size?: number
  userId?: string
  taskId?: number
  status?: string
  startDate?: string
  endDate?: string
}

export function listInstances(params: InstanceQueryParams = {}) {
  return http.get('/admin/instance', { params })
}

export function getInstanceDetail(id: number) {
  return http.get(`/admin/instance/${id}`)
}

export function getInstanceEvents(id: number) {
  return http.get(`/admin/instance/${id}/events`)
}
