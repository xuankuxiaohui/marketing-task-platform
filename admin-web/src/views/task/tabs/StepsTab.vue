<script setup lang="ts">
import { ref } from 'vue'
import FlowEditor from './steps/FlowEditor.vue'

const props = defineProps<{ taskId?: number | null }>()
const editorRef = ref()

defineExpose({
  getSteps: () => editorRef.value?.getSteps(),
  setSteps: (data: any) => editorRef.value?.setSteps(data),
  getTransitions: () => editorRef.value?.getTransitions(),
  setTransitions: (data: any) => editorRef.value?.setTransitions(data),
})
</script>

<template>
  <FlowEditor ref="editorRef" :task-id="taskId" />
</template>
