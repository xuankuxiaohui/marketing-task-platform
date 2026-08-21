<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Button, Empty, List, NavBar, PullRefresh, Tab, Tabs } from "vant";
import { isOk } from "@mkt/shared";
import { fetchDict, TASK_CATEGORY_DICT, dictLabel, type DictPortalEntry } from "@/api/dict";
import { fetchMineTasks, type MineTaskView } from "@/api/task";
import { resolveMineStatus, type MineTaskStatus } from "@/utils/mine-status";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatBeijing } from "@/utils/datetime";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { terminalStatusLabel } from "@/utils/task-button";

defineOptions({ name: "MineTasksPage" });

const ALL = "ALL";
const PAGE_SIZE = 20;
const STATUS_TABS = [
  { name: "IN_PROGRESS", title: zhCN.task.inProgress },
  { name: "COMPLETED", title: zhCN.task.completed },
  { name: "ABANDONED", title: zhCN.task.abandoned },
  { name: "EXPIRED", title: zhCN.task.expired },
] as const;

const router = useRouter();
const categories = ref<DictPortalEntry[]>([]);
const activeStatus = ref<MineTaskStatus>("IN_PROGRESS");
const activeCategory = ref(ALL);
const records = ref<MineTaskView[]>([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const loaded = ref(false);

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
  if (reset) {
    page.value = 1;
    finished.value = false;
  }
  loading.value = true;
  try {
    const status = resolveMineStatus(activeStatus.value);
    const rawCategory = String(activeCategory.value);
    const category = rawCategory === ALL || rawCategory === "0" ? undefined : rawCategory;
    const result = await fetchMineTasks({
      status,
      category,
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

function onStatusChange(name: string | number): void {
  const resolved = resolveMineStatus(name);
  if (resolved) {
    activeStatus.value = resolved;
  }
}

watch([activeStatus, activeCategory], () => {
  void loadPage(true);
});

onMounted(() => {
  void loadCategories();
  void loadPage(true);
});

defineExpose({ selectStatus: onStatusChange });
</script>

<template>
  <section class="mine-tasks">
    <NavBar :title="zhCN.mine.tasks" left-arrow @click-left="router.back()" />
    <Tabs v-model:active="activeStatus" sticky @change="onStatusChange">
      <Tab v-for="tab in STATUS_TABS" :key="tab.name" :title="tab.title" :name="tab.name">
        <template #title>
          <span :data-testid="'mine-status-' + tab.name">{{ tab.title }}</span>
        </template>
      </Tab>
    </Tabs>
    <Tabs v-model:active="activeCategory" shrink>
      <Tab :title="zhCN.task.all" :name="ALL" />
      <Tab
        v-for="entry in categories"
        :key="entry.value ?? entry.label"
        :title="entry.label || entry.value"
        :name="entry.value"
      />
    </Tabs>
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <Empty v-if="empty" :description="emptyCopy" data-testid="mine-tasks-empty">
        <Button
          v-if="activeStatus === 'IN_PROGRESS'"
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
.mine-task-card {
  display: flex;
  gap: 12px;
  align-items: center;
  width: calc(100% - 32px);
  margin: 0 16px 12px;
  padding: 12px;
  border: 0;
  border-radius: 12px;
  background: #fff;
  text-align: left;
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
  color: #646566;
  font-size: 12px;
}
</style>
