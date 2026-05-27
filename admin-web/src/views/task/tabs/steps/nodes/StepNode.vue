<script setup lang="ts">
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import type { StepNodeData } from '../types'

const props = defineProps<NodeProps<StepNodeData>>()

const TYPE_CLASSES: Record<string, string> = {
  CLICK: 'type-click',
  CALLBACK: 'type-callback',
  PROGRESS: 'type-progress',
  REWARD: 'type-reward',
  PASSIVE: 'type-passive',
}

const TYPE_LABELS: Record<string, string> = {
  CLICK: '点击',
  CALLBACK: '回调',
  PROGRESS: '进度',
  REWARD: '奖励',
  PASSIVE: '被动',
}
</script>

<template>
  <div :class="['step-node', { 'step-node-selected': selected }]">
    <Handle type="target" :position="Position.Top" />

    <span class="step-seq">{{ props.data.step.seq }}</span>
    <div class="step-info">
      <code class="step-code">{{ props.data.step.code }}</code>
      <span class="step-name">{{ props.data.step.name || '未命名' }}</span>
    </div>
    <span :class="['step-type-tag', TYPE_CLASSES[props.data.step.type] || 'type-click']">
      {{ TYPE_LABELS[props.data.step.type] || props.data.step.type }}
    </span>
    <span v-if="props.data.branchCount > 0" class="step-branch-badge">
      {{ props.data.branchCount }}分支
    </span>

    <Handle
      v-if="props.data.step.type !== 'REWARD'"
      type="source"
      :position="Position.Bottom"
    />
  </div>
</template>

<style scoped>
.step-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 264px;
  padding: 10px 12px;
  background: var(--color-surface);
  border: 2px solid var(--color-border);
  border-radius: 10px;
  transition: box-shadow 0.15s, border-color 0.15s;
  cursor: grab;
  user-select: none;
}

.step-node:hover {
  border-color: var(--color-brand-primary);
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.1);
}

.step-node-selected {
  border-color: var(--color-brand-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

.step-seq {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  background: linear-gradient(135deg, var(--color-brand-primary), var(--color-brand-secondary));
  color: var(--color-text-inverse);
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.step-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.step-code {
  font-family: var(--font-mono);
  font-size: 11px;
  background: var(--color-brand-primary-subtle);
  color: var(--color-brand-primary);
  padding: 1px 5px;
  border-radius: 3px;
  width: fit-content;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-type-tag {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

.type-click {
  background: var(--el-color-primary-light-8);
  color: var(--color-brand-primary-hover);
}

.type-callback {
  background: var(--color-amber-subtle);
  color: var(--color-amber-text);
}

.type-progress {
  background: var(--color-brand-primary-subtle);
  color: var(--color-brand-primary);
}

.type-reward {
  background: var(--color-emerald-subtle);
  color: var(--color-emerald-text);
}

.type-passive {
  background: var(--color-border-light);
  color: var(--color-text-muted);
}

.step-branch-badge {
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
  background: var(--color-amber-subtle, #fef3c7);
  color: var(--color-amber-text, #92400e);
  flex-shrink: 0;
}
</style>
