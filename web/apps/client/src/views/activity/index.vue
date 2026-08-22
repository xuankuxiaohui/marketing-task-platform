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
      <!-- richText is server-sanitized (R22); do not bind unsanitized HTML -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="activity-html" data-testid="activity-html" v-html="detail.richText" />
      <div v-if="hasSignin || tasks.length" data-testid="activity-submodules">
        <article
          v-if="hasSignin"
          class="signin-card"
          data-testid="activity-signin-card"
          role="button"
          tabindex="0"
          @click="openSignin"
        >
          <span class="signin-card__mark" aria-hidden="true">签</span>
          <span class="signin-card__meta">
            <strong>{{ zhCN.home.signin }}</strong>
            <span>{{ zhCN.home.signinHint }}</span>
          </span>
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
      <div class="activity-join">
        <Button type="primary" block data-testid="activity-join" @click="onParticipate">
          {{ zhCN.activity.join }}
        </Button>
      </div>
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
.activity-html {
  margin: 0 16px 12px;
  padding: 12px 16px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  font-size: 14px;
  line-height: 1.6;
}
.signin-card {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 12px 16px;
  padding: 14px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
  text-align: left;
}
.signin-card__mark {
  display: flex;
  width: 44px;
  height: 44px;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: var(--portal-accent-soft);
  color: var(--portal-accent);
  font-size: 16px;
  font-weight: 700;
}
.signin-card__meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}
.signin-card__meta strong {
  font-size: 16px;
}
.signin-card__meta span {
  color: var(--portal-muted);
  font-size: 13px;
}
.activity-join {
  padding: 8px 16px 24px;
}
</style>
