<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Empty, NavBar, showFailToast, showSuccessToast } from "vant";
import { isFail, isOk } from "@mkt/shared";
import {
  fetchActivities,
  fetchActivityDetail,
  postParticipate,
  type PortalActivityDetailView,
  type SubmoduleView,
} from "@/api/activity";
import { fetchTaskList, type TaskCardView } from "@/api/task";
import TaskCard from "@/components/TaskCard.vue";
import TaskCompleteSheet from "@/components/TaskCompleteSheet.vue";
import { zhCN } from "@/locales/zh-CN";
import { loginLocation } from "@/router/guards";
import { useSessionStore } from "@/store/session";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";

defineOptions({ name: "ActivityPage" });

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const detail = ref<PortalActivityDetailView | null>(null);
const result = ref<string | null>(null);
const tasks = ref<TaskCardView[]>([]);
const hasSignin = ref(false);
const sheetOpen = ref(false);
const sheetTaskId = ref<number | null>(null);

const activityId = computed(() => {
  const raw = route.query.id;
  if (typeof raw === "string" && raw) {
    return Number(raw);
  }
  return null;
});

function sortedSubmodules(rows: SubmoduleView[] | undefined): SubmoduleView[] {
  return [...(rows ?? [])].sort((a, b) => a.sort - b.sort);
}

async function loadBoundTasks(submodules: SubmoduleView[]): Promise<void> {
  const orderedIds = sortedSubmodules(submodules)
    .filter((item) => item.type === "TASK")
    .map((item) => item.refId);
  hasSignin.value = sortedSubmodules(submodules).some((item) => item.type === "SIGNIN");
  if (orderedIds.length === 0) {
    tasks.value = [];
    return;
  }
  const wanted = new Set(orderedIds);
  const found = new Map<number, TaskCardView>();
  let page = 1;
  while (found.size < wanted.size && page <= 20) {
    const list = await fetchTaskList({ page, pageSize: 50 });
    if (!isOk(list) || !list.data) {
      showPortalFail(list);
      break;
    }
    for (const card of list.data.records ?? []) {
      if (card.taskId != null && wanted.has(card.taskId)) {
        found.set(card.taskId, card);
      }
    }
    const total = Number(list.data.total ?? 0);
    const records = list.data.records ?? [];
    if (records.length === 0 || page * 50 >= total) {
      break;
    }
    page += 1;
  }
  tasks.value = orderedIds.map((id) => found.get(id)).filter((card): card is TaskCardView => card != null);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    let id = activityId.value;
    if (id == null || !Number.isFinite(id)) {
      const list = await fetchActivities();
      if (isOk(list) && list.data && list.data.length > 0) {
        id = list.data[0].id;
      }
    }
    if (id == null || !Number.isFinite(id)) {
      detail.value = null;
      tasks.value = [];
      hasSignin.value = false;
      return;
    }
    const response = await fetchActivityDetail(id);
    if (isFail(response)) {
      showFailToast(response.message);
      detail.value = null;
      tasks.value = [];
      hasSignin.value = false;
      return;
    }
    detail.value = response.data ?? null;
    await loadBoundTasks(detail.value?.submodules ?? []);
  } catch {
    showNetworkFail();
    detail.value = null;
    tasks.value = [];
    hasSignin.value = false;
  } finally {
    loading.value = false;
  }
}

function openTaskSheet(task: TaskCardView): void {
  if (task.taskId == null) {
    return;
  }
  sheetTaskId.value = task.taskId;
  sheetOpen.value = true;
}

function openSignin(): void {
  void router.push("/signin");
}

async function onParticipate(): Promise<void> {
  if (!session.authenticated) {
    void router.replace(loginLocation(route.fullPath));
    return;
  }
  if (!detail.value) {
    return;
  }
  const response = await postParticipate(detail.value.id);
  if (isFail(response)) {
    showFailToast(response.message);
    return;
  }
  result.value = response.data?.result ?? "";
  if (response.data?.result === "PASS") {
    showSuccessToast(zhCN.activity.joined);
  } else {
    showFailToast(zhCN.activity.rejected);
  }
}

watch(
  activityId,
  () => {
    void load();
  },
  { immediate: true },
);

watch(sheetOpen, (open, wasOpen) => {
  if (wasOpen && !open) {
    void loadBoundTasks(detail.value?.submodules ?? []);
  }
});
</script>

<template>
  <section class="activity-page">
    <NavBar :title="zhCN.activity.title" left-arrow @click-left="router.back()" />
    <Empty v-if="!loading && !detail" :description="zhCN.activity.empty" data-testid="activity-empty" />
    <div v-else-if="detail" data-testid="activity-detail">
      <h2 data-testid="activity-name">{{ detail.name }}</h2>
      <!-- richText is server-sanitized (R22); do not bind unsanitized HTML -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="activity-html" data-testid="activity-html" v-html="detail.richText" />
      <div v-if="hasSignin || tasks.length" data-testid="activity-submodules">
        <article
          v-if="hasSignin"
          class="hub-card"
          data-testid="activity-signin-card"
          role="button"
          tabindex="0"
          @click="openSignin"
        >
          <strong>{{ zhCN.home.signin }}</strong>
          <span>{{ zhCN.home.signinHint }}</span>
        </article>
        <TaskCard
          v-for="task in tasks"
          :key="task.taskId"
          :task="task"
          @open="openTaskSheet(task)"
          @action="openTaskSheet(task)"
        />
      </div>
      <p v-if="result" data-testid="activity-result">{{ result }}</p>
      <Button type="primary" block data-testid="activity-join" @click="onParticipate">
        {{ zhCN.activity.join }}
      </Button>
    </div>
    <TaskCompleteSheet v-model:show="sheetOpen" :task-id="sheetTaskId" />
  </section>
</template>

<style scoped>
.activity-html {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
}
.hub-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 12px 16px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
  text-align: left;
}
.hub-card strong {
  font-size: 15px;
}
.hub-card span {
  color: #646566;
  font-size: 13px;
}
</style>
