import { http } from './http'

export interface StepExtraItem {
  key: string
  value: string
}

export interface Step {
  id?: number
  taskId?: number
  seq: number
  code: string
  name: string
  description?: string
  type: string
  targetValue?: number
  callbackEventKey?: string
  rewardConfigJson?: string
  prizeId?: number
  prizeQuantity?: number
  flowDesc?: string
  extraJson?: string
}

export function listSteps(taskId: number) {
  return http.get(`/admin/task/${taskId}/steps`)
}

export function getStep(taskId: number, stepId: number) {
  return http.get(`/admin/task/${taskId}/steps/${stepId}`)
}

export function createStep(taskId: number, step: Step) {
  return http.post(`/admin/task/${taskId}/steps`, step)
}

export function updateStep(taskId: number, stepId: number, step: Step) {
  return http.put(`/admin/task/${taskId}/steps/${stepId}`, step)
}

export function deleteStep(taskId: number, stepId: number) {
  return http.delete(`/admin/task/${taskId}/steps/${stepId}`)
}

export function checkStepCode(taskId: number, code: string, excludeStepId?: number) {
  return http.get(`/admin/task/${taskId}/steps/check-code`, {
    params: { code, excludeStepId },
  })
}

export function reorderSteps(taskId: number, items: { id: number; seq: number }[]) {
  return http.put(`/admin/task/${taskId}/steps/reorder`, items)
}
