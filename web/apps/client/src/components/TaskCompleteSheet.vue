<script setup lang="ts">
import { computed } from "vue";
import { Popup } from "vant";
import TaskCompletePanel from "@/components/TaskCompletePanel.vue";

defineOptions({ name: "TaskCompleteSheet" });

const props = defineProps<{
  show: boolean;
  taskId: number | null;
}>();

const emit = defineEmits<{
  "update:show": [boolean];
}>();

const open = computed({
  get: () => props.show && props.taskId != null,
  set: (value: boolean) => emit("update:show", value),
});
</script>

<template>
  <Popup
    v-model:show="open"
    position="bottom"
    round
    closeable
    :style="{ height: '60vh', background: 'var(--portal-bg)' }"
    data-testid="task-complete-sheet"
  >
    <div class="task-complete-sheet">
      <TaskCompletePanel v-if="taskId != null" :task-id="taskId" />
    </div>
  </Popup>
</template>

<style scoped>
.task-complete-sheet {
  height: 100%;
  overflow: auto;
  background: var(--portal-bg);
}
</style>
