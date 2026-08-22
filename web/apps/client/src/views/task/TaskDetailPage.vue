<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import TaskCompletePanel from "@/components/TaskCompletePanel.vue";

defineOptions({ name: "TaskDetailPage" });

const route = useRoute();
const taskId = computed(() => Number(route.params.taskId));
</script>

<template>
  <TaskCompletePanel :task-id="taskId" show-nav />
</template>
