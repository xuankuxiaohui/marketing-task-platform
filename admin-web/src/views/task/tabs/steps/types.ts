import type { Step, StepTransition } from '../../../../api/step'

export interface StepNodeData {
  step: Step
  branchCount: number
}

export interface TransitionEdgeData {
  transition: StepTransition
  isImplicit: boolean
}

export type StepNodeType = 'step'
export type TransitionEdgeType = 'transition' | 'implicit'

export interface PaletteItem {
  type: string
  label: string
  color: string
  icon: string
}

export const PALETTE_ITEMS: PaletteItem[] = [
  { type: 'CLICK', label: '点击', color: 'var(--el-color-primary-light-8)', icon: '👆' },
  { type: 'CALLBACK', label: '回调', color: 'var(--color-amber-subtle)', icon: '🔗' },
  { type: 'PROGRESS', label: '进度', color: 'var(--color-brand-primary-subtle)', icon: '📊' },
  { type: 'REWARD', label: '奖励', color: 'var(--color-emerald-subtle)', icon: '🎁' },
  { type: 'PASSIVE', label: '被动', color: 'var(--color-border-light)', icon: '⏭' },
]
