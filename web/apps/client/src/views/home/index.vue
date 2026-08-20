<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Button, Empty, List, NavBar, PullRefresh, Tab, Tabs, showToast } from "vant";
import { isOk } from "@mkt/shared";
import { fetchDict, TASK_CATEGORY_DICT, type DictPortalEntry } from "@/api/dict";
import { fetchTaskList, startTask, type TaskCardView } from "@/api/task";
import TaskCard from "@/components/TaskCard.vue";
import { zhCN } from "@/locales/zh-CN";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { taskButtonState } from "@/utils/task-button";

defineOptions({ name: "HomePage" });

const ALL = "ALL";
const PAGE_SIZE = 20;

const router = useRouter();
const categories = ref<DictPortalEntry[]>([]);
const activeCategory = ref(ALL);
const records = ref<TaskCardView[]>([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const loaded = ref(false);

const empty = computed(() => loaded.value && records.value.length === 0);

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
    const result = await fetchTaskList({
      category: activeCategory.value === ALL ? undefined : activeCategory.value,
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

function openTask(task: TaskCardView): void {
  if (task.taskId == null) {
    return;
  }
  void router.push(`/task/${task.taskId}`);
}

async function onCardAction(task: TaskCardView): Promise<void> {
  const button = taskButtonState(task.userStatus);
  if (button.disabled || task.taskId == null) {
    return;
  }
  if (button.kind === "continue") {
    openTask(task);
    return;
  }
  try {
    const result = await startTask(task.taskId);
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    const status = result.data.instanceStatus;
    if (status === "IN_PROGRESS") {
      openTask(task);
      return;
    }
    showToast(taskButtonState(status).label);
    await loadPage(true);
  } catch {
    showNetworkFail();
  }
}

watch(activeCategory, () => {
  void loadPage(true);
});

onMounted(() => {
  void loadCategories();
  void loadPage(true);
});
</script>

<template>
  <section class="home-page">
    <NavBar :title="zhCN.home.title" />
    <Tabs v-model:active="activeCategory" shrink sticky>
      <Tab :title="zhCN.task.all" :name="ALL" />
      <Tab
        v-for="entry in categories"
        :key="entry.value ?? entry.label"
        :title="entry.label || entry.value"
        :name="entry.value"
      />
    </Tabs>
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <Empty v-if="empty" :description="zhCN.home.empty" data-testid="home-empty">
        <Button type="primary" size="small" data-testid="home-retry" @click="loadPage(true)">
          {{ zhCN.common.retry }}
        </Button>
      </Empty>
      <List
        v-else
        v-model:loading="loading"
        :finished="finished"
        :finished-text="zhCN.task.noMore"
        :immediate-check="false"
        data-testid="home-list"
        @load="onLoadMore"
      >
        <TaskCard
          v-for="task in records"
          :key="task.taskId"
          :task="task"
          @open="openTask(task)"
          @action="onCardAction(task)"
        />
      </List>
    </PullRefresh>
  </section>
</template>
