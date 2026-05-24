import { http } from './http'

export interface StepPlatformConfig {
  id?: number
  stepId?: number
  stepCode?: string
  platform: string
  buttonText?: string
  jumpType?: string
  jumpTarget?: string
  actionType?: string
  actionConfig?: string
}

export function listStepPlatforms(taskId: number) {
  return http.get(`/admin/task/${taskId}/step-platforms`)
}

export function batchSaveStepPlatforms(taskId: number, configs: StepPlatformConfig[]) {
  return http.put(`/admin/task/${taskId}/step-platforms`, configs)
}
