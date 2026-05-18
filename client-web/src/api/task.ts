import { http } from './http'

export interface Task {
  id: number
  code: string
  name: string
  description?: string
  periodType: string
  status: string
  version: number
}

export interface Step {
  id: number
  taskId: number
  seq: number
  code: string
  name: string
  type: string
  targetValue?: number
  flowDesc?: string
}

export interface StepPlatform {
  id: number
  stepId: number
  platform: string
  buttonText?: string
  jumpType?: string
  jumpTarget?: string
}

export interface TaskInstanceDetail {
  instance: {
    id: number
    userId: string
    taskId: number
    taskVersion: number
    cycleKey: string
    status: string
    currentStepSeq: number
    startTime?: string
    completeTime?: string
    rewardTime?: string
  }
  steps: Step[]
  stepPlatforms: StepPlatform[]
}

export function listTasks() {
  return http.get('/client/task/list')
}

export function getTaskDetail(taskId: number) {
  return http.get(`/client/task/${taskId}`)
}

export function clickStep(taskId: number, stepId: number) {
  return http.post(`/client/task/${taskId}/step/${stepId}/click`)
}

export function startTask(taskId: number) {
  return http.post(`/client/task/${taskId}/start`)
}
