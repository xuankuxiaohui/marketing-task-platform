<script setup lang="ts">
import { computed, nextTick, onMounted, onScopeDispose, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, DropdownItem, DropdownMenu, Empty, List, NavBar, PullRefresh, Tab, Tabs } from "vant";
import { isOk } from "@mkt/shared";
import { fetchActivities, type PortalActivityView } from "@/api/activity";
import { fetchDict, TASK_CATEGORY_DICT, dictLabel, type DictPortalEntry } from "@/api/dict";
import { fetchMineTasks, type MineTaskView } from "@/api/task";
import { usePagedList } from "@/composables/usePagedList";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { type MineTaskStatus } from "@/utils/mine-status";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatBeijing } from "@/utils/datetime";
import { ownersForTask, ownersLabel } from "@/utils/activity-ownership";
import { terminalStatusLabel } from "@/utils/task-button";

defineOptions({ name: "MineTasksPage" });

const ALL = "ALL";
const STATUS_TABS = [
  { name: ALL, title: zhCN.task.all },
  { name: "IN_PROGRESS", title: zhCN.task.inProgress },
  { name: "COMPLETED", title: zhCN.task.completed },
  { name: "EXPIRED", title: zhCN.task.expired },
] as const;

type StatusTab = (typeof STATUS_TABS)[number]["name"];

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const overlay = useLoginOverlayStore();
const isTabRoot = computed(() => route.meta.tab === "tasks");
const categories = ref<DictPortalEntry[]>([]);
const activeStatus = ref<StatusTab>("IN_PROGRESS");
const activeCategory = ref("");
const statusTabs = ref<{ resize?: () => void } | null>(null);
const activities = ref<PortalActivityView[]>([]);
let activitiesGeneration = 0;
let categoriesGeneration = 0;
let disposed = false;

const {
  records, loading, finished, refreshing, error, errorMessage, empty, loadMore, refresh, retry,
} = usePagedList<MineTaskView>({
  scope: () => [session.token, activeStatus.value, activeCategory.value],
  enabled: () => session.authenticated,
  fetchPage: (page, pageSize) => fetchMineTasks({
    status: activeStatus.value === ALL ? undefined : activeStatus.value,
    category: activeCategory.value || undefined,
    page,
    pageSize,
  }),
});

const categoryOptions = computed(() => [
  { text: zhCN.task.allCategories, value: "" },
  ...categories.value.map((category) => ({ text: category.label ?? category.value ?? "", value: category.value ?? "" })),
]);
const emptyCopy = computed(() => (activeStatus.value === "IN_PROGRESS" ? zhCN.empty.tasks : zhCN.task.finishedEmpty));

function activityLabel(row: MineTaskView): string {
  return ownersLabel(ownersForTask(row.taskId, activities.value));
}

async function loadActivities(): Promise<void> {
  const generation = ++activitiesGeneration;
  const token = session.token;
  activities.value = [];
  try {
    const result = await fetchActivities();
    if (!disposed && generation === activitiesGeneration && token === session.token) {
      activities.value = isOk(result) && result.data ? result.data : [];
    }
  } catch {
    if (!disposed && generation === activitiesGeneration && token === session.token) {
      activities.value = [];
    }
  }
}

async function loadCategories(): Promise<void> {
  const generation = ++categoriesGeneration;
  const token = session.token;
  if (!session.authenticated) {
    categories.value = [];
    return;
  }
  try {
    const result = await fetchDict(TASK_CATEGORY_DICT);
    if (!disposed && generation === categoriesGeneration && token === session.token
      && isOk(result) && Array.isArray(result.data)) {
      categories.value = result.data;
    }
  } catch {
    if (!disposed && generation === categoriesGeneration && token === session.token) {
      categories.value = [];
    }
  }
}

function openTask(row: MineTaskView): void {
  if (row.taskId == null) {
    return;
  }
  void router.push(`/task/${row.taskId}`);
}

function selectStatus(name: StatusTab | MineTaskStatus): void {
  activeStatus.value = name as StatusTab;
}

function requestLogin(): void {
  overlay.request({ redirect: route.fullPath });
}

watch(() => session.token, () => {
  categories.value = [];
  void loadCategories();
  void loadActivities();
}, { flush: "sync" });

function onRefresh(): void {
  void loadCategories();
  void loadActivities();
  void refresh();
}

onMounted(async () => {
  void loadCategories();
  void loadActivities();
  await nextTick();
  statusTabs.value?.resize?.();
});

onScopeDispose(() => {
  disposed = true;
  activitiesGeneration += 1;
  categoriesGeneration += 1;
});

defineExpose({ selectStatus });
</script>

<template>
  <section class="mine-tasks">
    <NavBar :title="zhCN.mine.tasks" :left-arrow="!isTabRoot" @click-left="isTabRoot ? undefined : router.back()" />
    <Tabs ref="statusTabs" v-model:active="activeStatus" data-testid="mine-status-row">
      <Tab
        v-for="tab in STATUS_TABS"
        :key="tab.name"
        :title="tab.title"
        :name="tab.name"
        :data-testid="'mine-status-' + tab.name"
      />
    </Tabs>
    <DropdownMenu data-testid="mine-task-categories">
      <DropdownItem v-model="activeCategory" :options="categoryOptions" />
    </DropdownMenu>
    <PullRefresh v-model="refreshing" class="mine-list" data-testid="mine-list" @refresh="onRefresh">
      <Empty v-if="!session.authenticated" :description="zhCN.session.missing" data-testid="mine-tasks-login">
        <Button type="primary" size="small" data-testid="mine-tasks-login-action" @click="requestLogin">
          {{ zhCN.login.submit }}
        </Button>
      </Empty>
      <Empty v-else-if="error && records.length === 0" :description="errorMessage" data-testid="mine-tasks-error">
        <Button type="primary" size="small" data-testid="mine-tasks-retry" @click="retry">
          {{ zhCN.common.retry }}
        </Button>
      </Empty>
      <Empty v-else-if="empty" :description="emptyCopy" data-testid="mine-tasks-empty">
        <Button
          v-if="activeStatus === 'IN_PROGRESS' || activeStatus === ALL"
          type="primary"
          size="small"
          data-testid="empty-go-home"
          @click="router.push('/home')"
        >
          {{ zhCN.empty.goHome }}
        </Button>
      </Empty>
      <List
        v-else
        v-model:loading="loading"
        v-model:error="error"
        :error-text="errorMessage"
        :finished="finished"
        :finished-text="zhCN.task.noMore"
        :immediate-check="false"
        data-testid="mine-tasks-list"
        @load="loadMore"
      >
        <button
          v-for="row in records"
          :key="row.instanceId"
          class="mine-task-card"
          type="button"
          data-testid="mine-task-card"
          @click="openTask(row)"
        >
          <FallbackImage :src="row.iconUrl" :alt="row.taskName ?? zhCN.mine.tasks" />
          <span class="mine-task-card__meta">
            <strong>{{ row.taskName }}</strong>
            <span v-if="row.currentStepName">{{ row.currentStepName }}</span>
            <span>{{ dictLabel(categories, row.category) }} · {{ terminalStatusLabel(row.status) }}</span>
            <span v-if="activityLabel(row)" data-testid="mine-task-activity">
              {{ zhCN.activity.owner }} {{ activityLabel(row) }}
            </span>
            <span v-if="row.startedAt">{{ formatBeijing(row.startedAt) }}</span>
          </span>
        </button>
      </List>
    </PullRefresh>
  </section>
</template>

<style scoped>
.mine-tasks {
  min-height: 100%;
  background: var(--portal-bg);
}
.mine-tasks :deep(.van-tabs__wrap),
.mine-tasks :deep(.van-tabs__nav) {
  background: var(--portal-bg);
}
.mine-list {
  padding-top: 12px;
}
.mine-task-card {
  display: flex;
  gap: 12px;
  align-items: center;
  width: calc(100% - 32px);
  margin: 0 16px 12px;
  padding: 12px;
  border: 0;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
  text-align: left;
}
.mine-task-card:first-of-type {
  margin-top: 4px;
}
.mine-task-card :deep(.fallback-image) {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 14px;
  background: var(--portal-primary-soft);
}
.mine-task-card__meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}
.mine-task-card__meta strong {
  font-size: 15px;
}
.mine-task-card__meta span {
  color: var(--portal-muted);
  font-size: 12px;
}
</style>
