<script setup lang="ts">
import { Button, Empty, NavBar } from "vant";
import { useRouter } from "vue-router";
import FallbackImage from "@/components/FallbackImage.vue";
import TaskTimeline from "@/components/TaskTimeline.vue";
import { useTaskComplete } from "@/composables/useTaskComplete";
import { zhCN } from "@/locales/zh-CN";
import { progressFraction, progressLabel } from "@/utils/timeline";
import { terminalStatusLabel } from "@/utils/task-button";

defineOptions({ name: "TaskCompletePanel" });

const props = defineProps<{
  taskId: number;
  showNav?: boolean;
}>();

const router = useRouter();
const {
  detail,
  acting,
  title,
  reward,
  currentAction,
  inProgress,
  notStarted,
  ended,
  status,
  load,
  onClaim,
  onCurrentAction,
  onAbandon,
} = useTaskComplete(() => props.taskId);
</script>

<template>
  <section class="task-detail">
    <NavBar v-if="showNav" :title="title" left-arrow @click-left="router.back()">
      <template #right>
        <button type="button" class="task-detail__refresh" data-testid="task-detail-refresh" @click="load">
          {{ zhCN.task.refresh }}
        </button>
      </template>
    </NavBar>
    <div v-else class="task-detail__sheet-head">
      <h2 class="task-detail__sheet-title">{{ title }}</h2>
      <button type="button" class="task-detail__refresh" data-testid="task-detail-refresh" @click="load">
        {{ zhCN.task.refresh }}
      </button>
    </div>
    <Empty v-if="ended" :description="terminalStatusLabel(status)" data-testid="task-detail-ended" />
    <div v-else-if="detail" class="task-detail__body">
      <header v-if="detail.task" class="task-detail__hero">
        <FallbackImage :src="detail.task.iconUrl" :alt="detail.task.name ?? title" />
        <div>
          <h2>{{ detail.task.name }}</h2>
          <p v-if="reward" data-testid="task-detail-reward">{{ reward }}</p>
          <p v-if="detail.task.description">{{ detail.task.description }}</p>
        </div>
      </header>
      <ul v-if="notStarted && detail.stepsPreview?.length" class="task-detail__preview" data-testid="task-steps-preview">
        <li v-for="step in detail.stepsPreview" :key="step.seq ?? step.name">{{ step.name }}</li>
      </ul>
      <TaskTimeline
        v-if="inProgress && detail.steps?.length"
        :steps="detail.steps"
        :current-step-code="detail.currentStep?.stepCode"
      />
      <div v-if="inProgress && detail.currentStep" class="task-detail__current" data-testid="task-current-step">
        <h3>{{ zhCN.task.currentStep }} · {{ detail.currentStep.name }}</h3>
        <p
          v-if="detail.currentStep.type === 'PROGRESS' && detail.currentStep.progressTarget != null"
          data-testid="task-current-progress"
        >
          {{ progressLabel(detail.currentStep.progressCurrent, detail.currentStep.progressTarget) }}
        </p>
        <div
          v-if="detail.currentStep.type === 'PROGRESS' && detail.currentStep.progressTarget != null"
          class="task-detail__bar"
          :style="{ width: `${progressFraction(detail.currentStep.progressCurrent, detail.currentStep.progressTarget)}%` }"
        />
        <Button
          v-if="currentAction.mode !== 'progress' && currentAction.mode !== 'none'"
          type="primary"
          block
          :disabled="currentAction.disabled || acting"
          :loading="acting"
          data-testid="task-step-action"
          @click="onCurrentAction"
        >
          {{ currentAction.buttonText }}
        </Button>
        <p v-else-if="currentAction.mode === 'none'" data-testid="task-none-action">{{ currentAction.buttonText }}</p>
      </div>
      <div class="task-detail__actions">
        <Button
          v-if="notStarted"
          type="primary"
          block
          :loading="acting"
          data-testid="task-claim"
          @click="onClaim"
        >
          {{ zhCN.task.claim }}
        </Button>
        <Button
          v-if="inProgress"
          block
          :disabled="acting"
          data-testid="task-abandon"
          @click="onAbandon"
        >
          {{ zhCN.task.abandon }}
        </Button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.task-detail {
  min-height: 100%;
  background: var(--portal-bg);
}
.task-detail__refresh {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--portal-primary);
  font-size: 14px;
}
.task-detail__sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px 0;
}
.task-detail__sheet-title {
  margin: 0;
  font-size: 16px;
}
.task-detail__hero {
  display: flex;
  gap: 12px;
  margin: 12px 16px;
  padding: 12px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.task-detail__hero h2 {
  margin: 0 0 4px;
  font-size: 16px;
}
.task-detail__hero p {
  margin: 0;
  color: var(--portal-muted);
  font-size: 13px;
}
.task-detail__preview {
  margin: 0 16px 12px;
  padding: 12px 12px 12px 28px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
}
.task-detail__current {
  margin: 12px 16px;
  padding: 16px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.task-detail__current h3 {
  margin: 0 0 8px;
  font-size: 15px;
}
.task-detail__bar {
  height: 6px;
  margin: 8px 0 16px;
  border-radius: 999px;
  background: var(--portal-primary);
}
.task-detail__actions {
  padding: 8px 16px 24px;
}
</style>
