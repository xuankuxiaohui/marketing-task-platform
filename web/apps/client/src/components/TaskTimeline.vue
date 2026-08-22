<script setup lang="ts">
import type { InstanceStepView } from "@/api/task";
import { progressFraction, progressLabel, timelineTone } from "@/utils/timeline";

defineOptions({ name: "TaskTimeline" });

defineProps<{
  steps: InstanceStepView[];
  currentStepCode?: string;
}>();
</script>

<template>
  <ol class="task-timeline" data-testid="task-timeline">
    <li
      v-for="step in steps"
      :key="step.stepCode ?? step.name"
      class="task-timeline__item"
      :data-testid="`timeline-step-${step.stepCode}`"
      :data-tone="timelineTone(step.status, step.stepCode, currentStepCode)"
    >
      <span class="task-timeline__mark" aria-hidden="true">
        {{ timelineTone(step.status, step.stepCode, currentStepCode) === "done" ? "✓" : "" }}
      </span>
      <span class="task-timeline__body">
        <strong>{{ step.name }}</strong>
        <template v-if="step.type === 'PROGRESS' && step.progressTarget != null">
          <span data-testid="timeline-progress-label">{{
            progressLabel(step.progressCurrent, step.progressTarget)
          }}</span>
          <div class="task-timeline__track" data-testid="timeline-progress-bar">
            <div
              class="task-timeline__bar"
              :style="{ width: `${progressFraction(step.progressCurrent, step.progressTarget)}%` }"
            />
          </div>
        </template>
      </span>
    </li>
  </ol>
</template>

<style scoped>
.task-timeline {
  margin: 0;
  padding: 0 16px;
  list-style: none;
}
.task-timeline__item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 0;
  color: var(--portal-line);
}
.task-timeline__item[data-tone="current"] {
  color: var(--portal-ink);
  font-weight: 600;
}
.task-timeline__item[data-tone="done"] {
  color: var(--portal-primary);
}
.task-timeline__mark {
  display: flex;
  width: 20px;
  height: 20px;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 12px;
}
.task-timeline__body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 6px;
}
.task-timeline__body strong {
  font-size: 14px;
  font-weight: inherit;
}
.task-timeline__track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--portal-primary-soft);
}
.task-timeline__bar {
  height: 100%;
  border-radius: 999px;
  background: var(--portal-primary);
}
</style>
