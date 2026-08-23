<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Empty, List, NavBar, PullRefresh, Tab, Tabs } from "vant";
import { isOk } from "@mkt/shared";
import { fetchDict, TASK_CATEGORY_DICT, dictLabel, type DictPortalEntry } from "@/api/dict";
import { fetchMineTasks, type MineTaskView } from "@/api/task";
import { useSessionReload } from "@/composables/useSessionReload";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { type MineTaskStatus } from "@/utils/mine-status";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatBeijing } from "@/utils/datetime";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { terminalStatusLabel } from "@/utils/task-button";

defineOptions({ name: "MineTasksPage" });

const ALL = "ALL";
const PAGE_SIZE = 20;
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
const records = ref<MineTaskView[]>([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const loaded = ref(false);
const statusTabs = ref<{ resize?: () => void } | null>(null);

const empty = computed(() => loaded.value && records.value.length === 0);
const emptyCopy = computed(() => (activeStatus.value === "IN_PROGRESS" ? zhCN.empty.tasks : zhCN.task.finishedEmpty));

async function loadCategories(): Promise<void> {
  try {
    const result = await fetchDict(TASK_CATEGORY_DICT);
    if (isOk(result) && Array.isArray(result.data)) {
      categories.value = result.data;
    }
  } catch {
    categories.value = [];
  }
}

async function loadPage(reset: boolean): Promise<void> {
  if (!session.authenticated) {
    records.value = [];
    total.value = 0;
    finished.value = true;
    loading.value = false;
    refreshing.value = false;
    loaded.value = true;
    return;
  }
  if (reset) {
    page.value = 1;
    finished.value = false;
  }
  loading.value = true;
  try {
    const status = activeStatus.value === ALL ? undefined : activeStatus.value;
    const result = await fetchMineTasks({
      status,
      page: page.value,
      pageSize: PAGE_SIZE,
    });
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      finished.value = true;
      return;
    }
    const next = result.data.records ?? [];
    total.value = Number(result.data.total ?? 0);
    records.value = reset ? next : [...records.value, ...next];
    page.value += 1;
    finished.value = records.value.length >= total.value || next.length === 0;
  } catch {
    showNetworkFail();
    finished.value = true;
  } finally {
    loading.value = false;
    refreshing.value = false;
    loaded.value = true;
  }
}

function onRefresh(): void {
  void loadPage(true);
}

function onLoadMore(): void {
  if (refreshing.value || loading.value) {
    return;
  }
  void loadPage(false);
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

watch(activeStatus, () => {
  void loadPage(true);
});

useSessionReload(() => {
  void loadCategories();
  void loadPage(true);
});

onMounted(async () => {
  void loadCategories();
  void loadPage(true);
  await nextTick();
  statusTabs.value?.resize?.();
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
    <PullRefresh v-model="refreshing" class="mine-list" data-testid="mine-list" @refresh="onRefresh">
      <Empty v-if="!session.authenticated" :description="zhCN.session.missing" data-testid="mine-tasks-login">
        <Button type="primary" size="small" data-testid="mine-tasks-login-action" @click="requestLogin">
          {{ zhCN.login.submit }}
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
        :finished="finished"
        :finished-text="zhCN.task.noMore"
        :immediate-check="false"
        data-testid="mine-tasks-list"
        @load="onLoadMore"
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
