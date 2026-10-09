import { computed, ref, toValue, watch, type MaybeRefOrGetter } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showConfirmDialog, showDialog, showToast } from "vant";
import { isOk } from "@mkt/shared";
import { fetchPrizeList, type PrizeCardView } from "@/api/prize";
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
import { useLatestRequest } from "@/composables/useLatestRequest";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { TRACK, track } from "@/tracking";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { resolvePortalRoute } from "@/utils/portal-route";
import { formatRewardPreview } from "@/utils/reward-preview";
import { actionHref, actionRouteName, stepActionUi } from "@/utils/step-action";
import { terminalStatusLabel } from "@/utils/task-button";

const GRANTS_PAGE_SIZE = 50;

export function useTaskComplete(taskIdSource: MaybeRefOrGetter<number>) {
  const route = useRoute();
  const router = useRouter();
  const session = useSessionStore();
  const overlay = useLoginOverlayStore();
  const detail = ref<TaskDetailView | null>(null);
  const grants = ref<PrizeCardView[]>([]);
  const loading = ref(false);
  const acting = ref(false);
  const waitingConfirm = ref(false);
  const viewed = ref(false);

  const taskId = computed(() => Number(toValue(taskIdSource)));
  const requestScope = () => [session.token, taskId.value];
  const beginDetailRequest = useLatestRequest(requestScope);
  const beginActionRequest = useLatestRequest(requestScope);
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
      status.value === "ABANDONED" ||
      status.value === "EXPIRED" ||
      (status.value === "COMPLETED" && grants.value.length === 0),
  );

  async function loadGrants(id: number, isCurrent: () => boolean): Promise<PrizeCardView[] | null> {
    if (!session.authenticated) {
      return [];
    }
    const matching: PrizeCardView[] = [];
    const seenIds = new Set<number>();
    let page = 1;
    let readCount = 0;
    let scanLimit = Infinity;

    while (isCurrent()) {
      const result = await fetchPrizeList({ tab: "ALL", page, pageSize: GRANTS_PAGE_SIZE });
      if (!isCurrent()) {
        return null;
      }
      if (!isOk(result) || !result.data) {
        showPortalFail(result);
        return null;
      }
      const rows = result.data.records ?? [];
      for (const row of rows) {
        if (row.sourceTaskId !== id || (row.recordId != null && seenIds.has(row.recordId))) {
          continue;
        }
        matching.push(row);
        if (row.recordId != null) {
          seenIds.add(row.recordId);
        }
      }
      readCount += rows.length;
      // New awards must not keep extending the scan; deletions may shorten it.
      scanLimit = Math.min(scanLimit, Number(result.data.total ?? readCount));
      if (rows.length === 0 || !Number.isFinite(scanLimit) || readCount >= scanLimit) {
        return matching;
      }
      page += 1;
    }
    return null;
  }

  async function load(): Promise<void> {
    const isCurrent = beginDetailRequest();
    const id = taskId.value;
    if (!isCurrent() || !Number.isFinite(id) || id <= 0) {
      return;
    }
    loading.value = true;
    try {
      const result = await fetchTaskDetail(id);
      if (!isCurrent()) {
        return;
      }
      if (!isOk(result) || !result.data) {
        showPortalFail(result);
        return;
      }
      detail.value = result.data;
      const nextGrants = await loadGrants(id, isCurrent);
      if (!isCurrent()) {
        return;
      }
      if (nextGrants != null) {
        grants.value = nextGrants;
      }
      if (!viewed.value) {
        viewed.value = true;
        track(TRACK.TASK_DETAIL_VIEW, { taskId: id });
      }
      if (result.data.status !== "IN_PROGRESS") {
        waitingConfirm.value = false;
      }
    } catch {
      if (isCurrent()) {
        showNetworkFail();
      }
    } finally {
      if (isCurrent()) {
        loading.value = false;
      }
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

  function requestGuestLogin(resume: () => void): boolean {
    if (session.authenticated) {
      return false;
    }
    const id = taskId.value;
    overlay.request({
      redirect: route.fullPath,
      resume: () => {
        if (taskId.value === id) {
          resume();
        }
      },
    });
    return true;
  }

  async function onClaim(): Promise<void> {
    if (acting.value) {
      return;
    }
    const isCurrent = beginActionRequest();
    if (!isCurrent() || requestGuestLogin(() => {
      void onClaim();
    })) {
      return;
    }
    const id = taskId.value;
    track(TRACK.TASK_START_CLICK, { taskId: id });
    acting.value = true;
    try {
      const result = await startTask(id);
      if (!isCurrent()) {
        return;
      }
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
      if (isCurrent()) {
        showNetworkFail();
      }
    } finally {
      if (isCurrent()) {
        acting.value = false;
      }
    }
  }

  async function onCurrentAction(): Promise<void> {
    if (acting.value) {
      return;
    }
    const isCurrent = beginActionRequest();
    if (!isCurrent() || requestGuestLogin(() => {
      void onCurrentAction();
    })) {
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
      if (!isCurrent()) {
        return;
      }
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
      if (isCurrent()) {
        showNetworkFail();
      }
    } finally {
      if (isCurrent()) {
        acting.value = false;
      }
    }
  }

  async function confirmAbandon(): Promise<boolean> {
    try {
      await showConfirmDialog({
        title: zhCN.task.abandon,
        message: zhCN.task.abandonConfirm,
      });
      return true;
    } catch {
      return false;
    }
  }

  async function onAbandon(): Promise<void> {
    if (acting.value) {
      return;
    }
    const isCurrent = beginActionRequest();
    if (!isCurrent() || requestGuestLogin(() => {
      void onAbandon();
    })) {
      return;
    }
    const instanceId = detail.value?.instanceId;
    if (instanceId == null) {
      return;
    }
    acting.value = true;
    try {
      if (!(await confirmAbandon()) || !isCurrent()) {
        return;
      }
      track(TRACK.TASK_ABANDON_CLICK, { instanceId });
      const result = await abandonTask(instanceId);
      if (!isCurrent()) {
        return;
      }
      if (!isOk(result) || !result.data) {
        showPortalFail(result);
        return;
      }
      showToast(zhCN.task.abandoned);
      await load();
    } catch {
      if (isCurrent()) {
        showNetworkFail();
      }
    } finally {
      if (isCurrent()) {
        acting.value = false;
      }
    }
  }

  watch(
    requestScope,
    () => {
      viewed.value = false;
      detail.value = null;
      grants.value = [];
      waitingConfirm.value = false;
      loading.value = false;
      acting.value = false;
      void load();
    },
    { immediate: true, flush: "sync" },
  );

  return {
    detail,
    grants,
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
