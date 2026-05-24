import { http } from './http'

export interface ImpersonateRequest {
  userId: string
  province: string
  role: string
  tags: string[]
  level: number
  platform: string
}

export function impersonate(body: ImpersonateRequest) {
  return http.post('/admin/simulate/impersonate', body)
}

export function clearImpersonate() {
  return http.delete('/admin/simulate/impersonate')
}

export function simulateCallback(instanceId: number, eventKey: string) {
  return http.post('/admin/simulate/callback', { instanceId, eventKey })
}

export function simulateProgress(instanceId: number, stepId: number, progressValue: number) {
  return http.post('/admin/simulate/progress', { instanceId, stepId, progressValue })
}

export function simulateFullFlow(taskId: number) {
  return http.post(`/admin/simulate/full-flow/${taskId}`)
}

export function getSimulateStatus() {
  return http.get('/admin/simulate/status')
}

// ---- new standalone simulation APIs ----

export interface TestUser {
  userId: string
}

export interface SimulateFlowRequest {
  userId: string
  taskId: number
  province?: string
  platform?: string
}

export interface StepStatusItem {
  stepId: number
  seq: number
  code: string
  name: string
  description: string
  type: string
  targetValue: number | null
  callbackEventKey: string | null
  flowDesc: string
  isCurrentStep: boolean
  progressStatus: string | null
  progressValue: number | null
}

export interface SimulateInstanceDetail {
  instance: {
    id: number
    userId: string
    taskId: number
    taskVersion: number
    cycleKey: string
    status: string
    currentStepSeq: number
    startTime: string | null
    completeTime: string | null
    rewardTime: string | null
  }
  steps: StepStatusItem[]
}

export interface SimulateEvent {
  id: number
  eventType: string
  taskId: number
  instanceId: number
  stepId: number | null
  userId: string
  platform: string | null
  eventData: string | null
  createdAt: string
}

export function getTestUsers() {
  return http.get<{ code: number; data: TestUser[] }>('/admin/simulate/test-users')
}

export function startSimulateFlow(body: SimulateFlowRequest) {
  return http.post<{ code: number; data: SimulateInstanceDetail }>('/admin/simulate/flow', body)
}

export function getSimulateInstanceDetail(instanceId: number) {
  return http.get<{ code: number; data: SimulateInstanceDetail }>(`/admin/simulate/instance/${instanceId}/detail`)
}

export function simulateClick(instanceId: number, stepId: number) {
  return http.post<{ code: number; data: SimulateInstanceDetail }>('/admin/simulate/click', { instanceId, stepId })
}

export function getSimulateInstanceEvents(instanceId: number) {
  return http.get<{ code: number; data: SimulateEvent[] }>(`/admin/simulate/instance/${instanceId}/events`)
}
