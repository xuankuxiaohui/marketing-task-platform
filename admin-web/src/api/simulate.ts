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
