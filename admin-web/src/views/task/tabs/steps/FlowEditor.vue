<script setup lang="ts">
import { createFlowEditor, provideFlowEditor } from './composables/useFlowEditor'
import FlowCanvas from './FlowCanvas.vue'
import NodePalette from './NodePalette.vue'
import PropertyPanel from './PropertyPanel.vue'

const props = defineProps<{ taskId?: number | null }>()

const editor = createFlowEditor(props.taskId)
provideFlowEditor(editor)

defineExpose({
  getSteps: () => editor.getSteps(),
  setSteps: (data: any) => editor.setSteps(data),
  getTransitions: () => editor.getTransitions(),
  setTransitions: (data: any) => editor.setTransitions(data),
})
</script>

<template>
  <div class="flow-editor">
    <NodePalette />
    <FlowCanvas />
    <PropertyPanel :task-id="taskId" />
  </div>
</template>

<style scoped>
.flow-editor {
  display: flex;
  height: 640px;
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-surface);
}
</style>
