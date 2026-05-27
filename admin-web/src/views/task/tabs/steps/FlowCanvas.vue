<script setup lang="ts">
import { VueFlow, useVueFlow, type Connection } from '@vue-flow/core'
import { MiniMap } from '@vue-flow/minimap'
import { Controls } from '@vue-flow/controls'
import { Background } from '@vue-flow/background'
import { useFlowEditor } from './composables/useFlowEditor'
import StepNode from './nodes/StepNode.vue'
import TransitionEdge from './edges/TransitionEdge.vue'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'
import '@vue-flow/controls/dist/style.css'

const {
  nodes,
  edges,
  selectedNodeId,
  selectedEdgeId,
  selectNode,
  selectEdge,
  clearSelection,
  addConnection,
  removeEdge,
  removeNode,
  addNodeFromPalette,
  saveNodePositions,
} = useFlowEditor()

const { onConnect, onNodeClick, onEdgeClick, onPaneClick, onNodesChange } = useVueFlow({
  id: 'flow-editor',
})

onConnect((params: Connection) => {
  if (params.source && params.target) {
    addConnection(params.source, params.target)
  }
})

onNodeClick(({ node }) => {
  selectNode(node.id)
})

onEdgeClick(({ edge }) => {
  selectEdge(edge.id)
})

onPaneClick(() => {
  clearSelection()
})

onNodesChange((changes) => {
  for (const change of changes) {
    if (change.type === 'position' && change.position) {
      const node = nodes.value.find(n => n.id === change.id)
      if (node) {
        node.position = change.position
      }
    }
  }
  saveNodePositions()
})

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Delete' || event.key === 'Backspace') {
    if (selectedEdgeId.value) {
      removeEdge(selectedEdgeId.value)
    } else if (selectedNodeId.value) {
      removeNode(selectedNodeId.value)
    }
  }
  if (event.key === 'Escape') {
    clearSelection()
  }
}

function onDrop(event: DragEvent) {
  const type = event.dataTransfer?.getData('application/vueflow')
  if (!type) return
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const position = {
    x: event.clientX - bounds.left - 132,
    y: event.clientY - bounds.top - 30,
  }
  addNodeFromPalette(type, position)
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}
</script>

<template>
  <div
    class="flow-canvas"
    tabindex="0"
    @keydown="onKeyDown"
    @drop="onDrop"
    @dragover="onDragOver"
  >
    <VueFlow
      id="flow-editor"
      :nodes="nodes"
      :edges="edges"
      :default-viewport="{ zoom: 0.9, x: 0, y: 0 }"
      :min-zoom="0.2"
      :max-zoom="2"
      :snap-to-grid="true"
      :snap-grid="[16, 16]"
      fit-view-on-init
    >
      <template #node-step="stepProps">
        <StepNode v-bind="stepProps" />
      </template>

      <template #edge-transition="edgeProps">
        <TransitionEdge v-bind="edgeProps" />
      </template>

      <template #edge-implicit="edgeProps">
        <TransitionEdge v-bind="edgeProps" />
      </template>

      <MiniMap :pannable="true" :zoomable="true" />
      <Controls />
      <Background :gap="20" :size="1" />
    </VueFlow>
  </div>
</template>

<style scoped>
.flow-canvas {
  flex: 1;
  min-height: 500px;
  outline: none;
}

.flow-canvas :deep(.vue-flow__minimap) {
  border-radius: 8px;
  border: 1px solid var(--color-border-light);
}

.flow-canvas :deep(.vue-flow__controls) {
  border-radius: 8px;
  border: 1px solid var(--color-border-light);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.flow-canvas :deep(.vue-flow__node) {
  cursor: grab;
}

.flow-canvas :deep(.vue-flow__handle) {
  width: 10px;
  height: 10px;
  background: var(--color-brand-primary);
  border: 2px solid white;
}

.flow-canvas :deep(.vue-flow__handle:hover) {
  background: var(--color-brand-primary-hover);
}
</style>
