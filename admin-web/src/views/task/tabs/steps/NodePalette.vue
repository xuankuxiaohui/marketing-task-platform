<script setup lang="ts">
import { PALETTE_ITEMS } from './types'
import { useNodePalette } from './composables/useNodePalette'

const { onDragStart } = useNodePalette()
</script>

<template>
  <div class="node-palette">
    <div class="palette-title">步骤类型</div>
    <div class="palette-hint">拖拽到画布添加</div>
    <div
      v-for="item in PALETTE_ITEMS"
      :key="item.type"
      class="palette-card"
      draggable="true"
      @dragstart="onDragStart($event, item.type)"
    >
      <span class="palette-icon">{{ item.icon }}</span>
      <span class="palette-label">{{ item.label }}</span>
      <span class="palette-type">{{ item.type }}</span>
    </div>
  </div>
</template>

<style scoped>
.node-palette {
  width: 180px;
  flex-shrink: 0;
  padding: 12px;
  border-right: 1px solid var(--color-border-light);
  background: var(--color-surface);
  overflow-y: auto;
}

.palette-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.palette-hint {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-bottom: 12px;
}

.palette-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s;
  user-select: none;
}

.palette-card:hover {
  border-color: var(--color-brand-primary);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
}

.palette-card:active {
  cursor: grabbing;
  opacity: 0.7;
}

.palette-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.palette-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-primary);
  flex: 1;
}

.palette-type {
  font-size: 9px;
  font-weight: 700;
  color: var(--color-text-muted);
  text-transform: uppercase;
}
</style>
