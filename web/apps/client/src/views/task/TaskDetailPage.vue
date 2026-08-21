<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Empty, NavBar, showConfirmDialog, showDialog, showToast } from "vant";
import { isOk } from "@mkt/shared";
import {
  abandonTask,
  clickTaskStep,
  fetchTaskDetail,
  startTask,
  type CurrentStepView,
  type RewardFeedbackView,
  type TaskDetailView,
} from "@/api/task";
import FallbackImage from "@/components/FallbackImage.vue";
import TaskTimeline from "@/components/TaskTimeline.vue";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { formatRewardPreview } from "@/utils/reward-preview";
import { actionHref, actionRouteName, stepActionUi } from "@/utils/step-action";
import { resolvePortalRoute } from "@/utils/portal-route";
import { progressFraction, progressLabel } from "@/utils/timeline";
import { terminalStatusLabel } from "@/utils/task-button";

defineOptions({ name: "TaskDetailPage" });

const route = useRoute();
const router = useRouter();
const detail = ref<TaskDetailView | null>(null);
const loading = ref(false);
const acting = ref(false);
const waitingConfirm = ref(false);
const viewed = ref(false);

const taskId = computed(() => Number(route.params.taskId));
const status = computed(() => detail.value?.status);
const title = computed(() => {
  return detail.value?.task?.name || detail.value?.currentStep?.name || zhCN.home.title;
});
const reward = computed(() => formatRewardPreview(detail.value?.rewardPreview));
const currentAction = computed(() => stepActionUi(detail.value?.currentStep, waitingConfirm.value));
const inProgress = computed(() => status.value === "IN_PROGRESS");
const notStarted = computed(() => status.value === "NOT_STARTED");
const ended = computed(() => status.value === "OFFLINE" || status.value === "COMPLETED" || status.value === "ABANDONED" || status.value === "EXPIRED");

async function load(): Promise<void> {
  if (!Number.isFinite(taskId.value)) {
    return;
  }
  loading.value = true;
  try {
    const result = await fetchTaskDetail(taskId.value);
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    detail.value = result.data;
    if (!viewed.value) {
      viewed.value = true;
      track(TRACK.TASK_DETAIL_VIEW, { taskId: taskId.value });
    }
    if (result.data.status !== "IN_PROGRESS") {
      waitingConfirm.value = false;
    }
  } catch {
    showNetworkFail();
  } finally {
    loading.value = false;
  }
}

function openHref(href: string): void {
  globalThis.open(href, "_blank", "noopener");
}

function followAction(step: CurrentStepView | undefined): void {
  if (!step?.action) {
    return;
  }
  const href = actionHref(step.action);
  if (href) {
    openHref(href);
    return;
  }
  const routeName = actionRouteName(step.action);
  if (!routeName) {
    return;
  }
  const path = resolvePortalRoute(routeName, step.action.params, taskId.value);
  if (path && path !== route.fullPath) {
    void router.push(path);
  }
}

function showReward(items?: RewardFeedbackView[] | null): void {
  const lines = (items ?? [])
    .filter((item) => item.prizeName)
    .map((item) => `${item.prizeName} × ${item.count ?? 1}`);
  if (lines.length === 0) {
    showToast(zhCN.task.rewardEmpty);
    return;
  }
  void showDialog({
    title: zhCN.task.rewardTitle,
    message: lines.join("\n"),
    confirmButtonText: zhCN.common.confirm,
  });
}

async function onClaim(): Promise<void> {
  track(TRACK.TASK_START_CLICK, { taskId: taskId.value });
  acting.value = true;
  try {
    const result = await startTask(taskId.value);
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    if (result.data.instanceStatus === "IN_PROGRESS") {
      await load();
      return;
    }
    showToast(terminalStatusLabel(result.data.instanceStatus));
    await load();
  } catch {
    showNetworkFail();
  } finally {
    acting.value = false;
  }
}

async function onCurrentAction(): Promise<void> {
  const step = detail.value?.currentStep;
  const instanceId = detail.value?.instanceId;
  if (!step || instanceId == null) {
    return;
  }
  const ui = currentAction.value;
  if (ui.disabled) {
    return;
  }
  if (ui.mode === "callback-go") {
    followAction(step);
    waitingConfirm.value = true;
    return;
  }
  acting.value = true;
  try {
    if (ui.mode === "click") {
      followAction(step);
    }
    if (!step.stepCode) {
      return;
    }
    track(TRACK.TASK_STEP_CLICK, { instanceId, stepCode: step.stepCode });
    const result = await clickTaskStep(instanceId, step.stepCode);
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    if (result.data.instanceStatus === "COMPLETED") {
      track(TRACK.TASK_COMPLETE_VIEW, { instanceId, taskId: taskId.value });
      showReward(result.data.rewardFeedback);
    }
    waitingConfirm.value = false;
    await load();
  } catch {
    showNetworkFail();
  } finally {
    acting.value = false;
  }
}

async function onAbandon(): Promise<void> {
  const instanceId = detail.value?.instanceId;
  if (instanceId == null) {
    return;
  }
  try {
    await showConfirmDialog({
      title: zhCN.task.abandon,
      message: zhCN.task.abandonConfirm,
    });
  } catch {
    return;
  }
  track(TRACK.TASK_ABANDON_CLICK, { instanceId });
  acting.value = true;
  try {
    const result = await abandonTask(instanceId);
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    showToast(zhCN.task.abandoned);
    await load();
  } catch {
    showNetworkFail();
  } finally {
    acting.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="task-detail">
    <NavBar :title="title" left-arrow @click-left="router.back()">
      <template #right>
        <button type="button" class="task-detail__refresh" data-testid="task-detail-refresh" @click="load">
          {{ zhCN.task.refresh }}
        </button>
      </template>
    </NavBar>
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
.task-detail__refresh {
  padding: 0;
  border: 0;
  background: transparent;
  color: #1989fa;
  font-size: 14px;
}
.task-detail__hero {
  display: flex;
  gap: 12px;
  margin: 12px 16px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
}
.task-detail__hero h2 {
  margin: 0 0 4px;
  font-size: 16px;
}
.task-detail__hero p {
  margin: 0;
  color: #646566;
  font-size: 13px;
}
.task-detail__preview {
  margin: 0 16px 12px;
  padding: 12px 12px 12px 28px;
  border-radius: 12px;
  background: #fff;
}
.task-detail__current {
  margin: 12px 16px;
  padding: 16px;
  border-radius: 12px;
  background: #fff;
}
.task-detail__current h3 {
  margin: 0 0 8px;
  font-size: 15px;
}
.task-detail__bar {
  height: 6px;
  margin: 8px 0 16px;
  border-radius: 999px;
  background: #1989fa;
}
.task-detail__actions {
  padding: 8px 16px 24px;
}
</style>
