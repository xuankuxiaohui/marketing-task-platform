import { computed, ref, toValue, watch, type MaybeRefOrGetter } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showConfirmDialog, showDialog, showToast } from "vant";
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
import { zhCN } from "@/locales/zh-CN";
import { loginLocation } from "@/router/guards";
import { useSessionStore } from "@/store/session";
import { TRACK, track } from "@/tracking";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { resolvePortalRoute } from "@/utils/portal-route";
import { formatRewardPreview } from "@/utils/reward-preview";
import { actionHref, actionRouteName, stepActionUi } from "@/utils/step-action";
import { terminalStatusLabel } from "@/utils/task-button";

export function useTaskComplete(taskIdSource: MaybeRefOrGetter<number>) {
  const route = useRoute();
  const router = useRouter();
  const session = useSessionStore();
  const detail = ref<TaskDetailView | null>(null);
  const loading = ref(false);
  const acting = ref(false);
  const waitingConfirm = ref(false);
  const viewed = ref(false);

  const taskId = computed(() => Number(toValue(taskIdSource)));
  const status = computed(() => detail.value?.status);
  const title = computed(() => {
    return detail.value?.task?.name || detail.value?.currentStep?.name || zhCN.task.title;
  });
  const reward = computed(() => formatRewardPreview(detail.value?.rewardPreview));
  const currentAction = computed(() => stepActionUi(detail.value?.currentStep, waitingConfirm.value));
  const inProgress = computed(() => status.value === "IN_PROGRESS");
  const notStarted = computed(() => status.value === "NOT_STARTED");
  const ended = computed(
    () =>
      status.value === "OFFLINE" ||
      status.value === "COMPLETED" ||
      status.value === "ABANDONED" ||
      status.value === "EXPIRED",
  );

  async function load(): Promise<void> {
    if (!Number.isFinite(taskId.value) || taskId.value <= 0) {
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

  function redirectGuestToLogin(): boolean {
    if (session.authenticated) {
      return false;
    }
    void router.replace(loginLocation(route.fullPath));
    return true;
  }

  async function onClaim(): Promise<void> {
    if (redirectGuestToLogin()) {
      return;
    }
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
    if (redirectGuestToLogin()) {
      return;
    }
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
    if (redirectGuestToLogin()) {
      return;
    }
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

  watch(
    taskId,
    (id, previous) => {
      if (id === previous) {
        return;
      }
      viewed.value = false;
      detail.value = null;
      waitingConfirm.value = false;
      void load();
    },
    { immediate: true },
  );

  return {
    detail,
    loading,
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
  };
}
