<script setup lang="ts">
import { computed } from 'vue'
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@vue-flow/core'
import type { TransitionEdgeData } from '../types'

const props = defineProps<EdgeProps<TransitionEdgeData>>()

const path = computed(() => getBezierPath(props))

const isImplicit = computed(() => props.data?.isImplicit ?? false)
</script>

<template>
  <BaseEdge
    :id="id"
    :path="path[0]"
    :marker-end="markerEnd"
    :style="{
      stroke: isImplicit ? '#94a3b8' : '#3b82f6',
      strokeWidth: isImplicit ? 1.5 : 2,
      strokeDasharray: isImplicit ? '6 4' : 'none',
    }"
  />
  <EdgeLabelRenderer>
    <div
      v-if="!isImplicit"
      :style="{
        pointerEvents: 'all',
        position: 'absolute',
        transform: `translate(-50%, -50%) translate(${path[1]}px,${path[2]}px)`,
      }"
      class="edge-label nodrag nopan"
      :class="{ 'edge-label-selected': selected }"
    >
      <span class="edge-prio">P{{ data?.transition.priority ?? 0 }}</span>
      <span v-if="data?.transition.conditionExpr" class="edge-cond">
        {{ data.transition.conditionExpr }}
      </span>
      <span v-else class="edge-default">默认</span>
    </div>
  </EdgeLabelRenderer>
</template>

<style scoped>
.edge-label {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  font-size: 10px;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.edge-label:hover {
  border-color: var(--color-brand-primary);
}

.edge-label-selected {
  border-color: var(--color-brand-primary);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}

.edge-prio {
  font-weight: 700;
  color: var(--color-brand-primary);
}

.edge-cond {
  color: var(--color-text-secondary);
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--font-mono);
  font-size: 10px;
}

.edge-default {
  color: var(--color-emerald-text, #065f46);
  font-weight: 600;
}
</style>
