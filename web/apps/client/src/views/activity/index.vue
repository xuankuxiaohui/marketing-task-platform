<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Empty, NavBar, showFailToast } from "vant";
import { isFail, isOk } from "@mkt/shared";
import {
  fetchActivities,
  fetchActivityDetail,
  type PortalActivityDetailView,
  type SubmoduleView,
} from "@/api/activity";
import { fetchTaskList, type TaskCardView } from "@/api/task";
import FallbackImage from "@/components/FallbackImage.vue";
import TaskCard from "@/components/TaskCard.vue";
import TaskCompleteSheet from "@/components/TaskCompleteSheet.vue";
import { zhCN } from "@/locales/zh-CN";
import { activityCover, activityWindow } from "@/utils/activity-cover";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";

defineOptions({ name: "ActivityPage" });

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<PortalActivityDetailView | null>(null);
const tasks = ref<TaskCardView[]>([]);
const sheetOpen = ref(false);
const sheetTaskId = ref<number | null>(null);
const rulesOpen = ref(false);
const rulesEl = ref<HTMLElement | null>(null);

const activityId = computed(() => {
  const raw = route.query.id;
  if (typeof raw === "string" && raw) {
    return Number(raw);
  }
  return null;
});

const cover = computed(() => (detail.value ? activityCover(detail.value) : undefined));
const windowLabel = computed(() =>
  detail.value ? activityWindow(detail.value.startTime, detail.value.endTime) : "",
);

function sortedSubmodules(rows: SubmoduleView[] | undefined): SubmoduleView[] {
  return [...(rows ?? [])].sort((a, b) => a.sort - b.sort);
}

async function loadBoundTasks(submodules: SubmoduleView[]): Promise<void> {
  const orderedIds = sortedSubmodules(submodules)
    .filter((item) => item.type === "TASK")
    .map((item) => item.refId);
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
  rulesOpen.value = false;
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
      return;
    }
    const response = await fetchActivityDetail(id);
    if (isFail(response)) {
      showFailToast(response.message);
      detail.value = null;
      tasks.value = [];
      return;
    }
    detail.value = response.data ?? null;
    await loadBoundTasks(detail.value?.submodules ?? []);
  } catch {
    showNetworkFail();
    detail.value = null;
    tasks.value = [];
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

async function openRules(): Promise<void> {
  rulesOpen.value = true;
  await nextTick();
  rulesEl.value?.scrollIntoView?.({ behavior: "smooth", block: "start" });
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
      <header class="activity-hero">
        <div class="activity-hero__cover">
          <FallbackImage v-if="cover" :src="cover" :alt="detail.name" />
          <span v-else class="activity-hero__fallback">{{ detail.name.slice(0, 1) }}</span>
        </div>
        <div class="activity-hero__body">
          <h2 data-testid="activity-name">{{ detail.name }}</h2>
          <p v-if="windowLabel">{{ windowLabel }}</p>
        </div>
      </header>
      <div v-if="tasks.length" data-testid="activity-submodules">
        <TaskCard
          v-for="task in tasks"
          :key="task.taskId"
          :task="task"
          @open="openTaskSheet(task)"
          @action="openTaskSheet(task)"
        />
      </div>
      <section v-if="rulesOpen" ref="rulesEl" class="activity-rules" data-testid="activity-rules">
        <h3>{{ zhCN.activity.rulesTitle }}</h3>
        <!-- richText is server-sanitized (R22); do not bind unsanitized HTML -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <div class="activity-html" data-testid="activity-html" v-html="detail.richText" />
      </section>
      <button type="button" class="activity-rules-fab" data-testid="activity-rules-btn" @click="openRules">
        {{ zhCN.activity.rules }}
      </button>
    </div>
    <TaskCompleteSheet v-model:show="sheetOpen" :task-id="sheetTaskId" />
  </section>
</template>

<style scoped>
.activity-page {
  min-height: 100%;
  background:
    radial-gradient(120% 50% at 50% -10%, var(--portal-bg-wash) 0%, transparent 50%),
    var(--portal-bg);
}
.activity-page :deep(.van-nav-bar) {
  background: transparent;
}
.activity-hero {
  overflow: hidden;
  margin: 8px 16px 12px;
  border-radius: var(--portal-radius-lg);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow);
}
.activity-hero__cover {
  display: flex;
  height: 140px;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, var(--portal-primary-warm) 0%, var(--portal-primary-deep) 100%);
}
.activity-hero__cover :deep(.fallback-image),
.activity-hero__cover :deep(img) {
  width: 100%;
  height: 140px;
  border-radius: 0;
}
.activity-hero__fallback {
  color: #fff;
  font-size: 48px;
  font-weight: 700;
}
.activity-hero__body {
  padding: 14px 16px 16px;
}
.activity-hero__body h2 {
  margin: 0;
  font-size: 18px;
}
.activity-hero__body p {
  margin: 6px 0 0;
  color: var(--portal-muted);
  font-size: 13px;
}
.activity-rules {
  margin: 0 16px 80px;
  padding: 12px 16px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
}
.activity-rules h3 {
  margin: 0 0 8px;
  font-size: 15px;
}
.activity-html {
  font-size: 14px;
  line-height: 1.6;
}
.activity-rules-fab {
  position: fixed;
  top: 40%;
  right: 0;
  z-index: 8;
  padding: 10px 8px;
  border: 0;
  border-radius: 10px 0 0 10px;
  background: var(--portal-primary);
  box-shadow: var(--portal-shadow);
  color: #fff;
  font-size: 13px;
  writing-mode: vertical-rl;
}
</style>
