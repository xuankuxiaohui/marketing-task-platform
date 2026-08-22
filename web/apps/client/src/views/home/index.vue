<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Button, Empty, NavBar, PullRefresh, showSuccessToast } from "vant";
import { isFail, isOk } from "@mkt/shared";
import { fetchActivities, type PortalActivityView } from "@/api/activity";
import { fetchPointsBalance } from "@/api/points";
import {
  fetchSigninActivities,
  fetchSigninCalendar,
  postCheckin,
  type SigninCalendarResponse,
} from "@/api/signin";
import { fetchTaskList, type TaskCardView } from "@/api/task";
import AdCarousel from "@/components/AdCarousel.vue";
import FallbackImage from "@/components/FallbackImage.vue";
import TaskCard from "@/components/TaskCard.vue";
import TaskCompleteSheet from "@/components/TaskCompleteSheet.vue";
import { zhCN } from "@/locales/zh-CN";
import { activityCover, activityWindow } from "@/utils/activity-cover";
import { homeSigninWeek, toIsoDate } from "@/utils/home-week";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { isGuestSessionCode } from "@/utils/session-reason";

defineOptions({ name: "HomePage" });

const TODAY_TASK_LIMIT = 5;

const router = useRouter();
const activities = ref<PortalActivityView[]>([]);
const tasks = ref<TaskCardView[]>([]);
const refreshing = ref(false);
const loaded = ref(false);
const pointsBalance = ref<number | null>(null);
const pointsGuest = ref(false);
const signinCalendar = ref<SigninCalendarResponse | null>(null);
const signinActivityId = ref<number | null>(null);
const signinActing = ref(false);
const sheetOpen = ref(false);
const sheetTaskId = ref<number | null>(null);

const emptyActivities = computed(() => loaded.value && activities.value.length === 0);
const showPointsBar = computed(() => pointsBalance.value != null || pointsGuest.value);
const todayIso = computed(() => toIsoDate(new Date()));
const week = computed(() => homeSigninWeek(signinCalendar.value?.days ?? [], todayIso.value));
const todaySigned = computed(() =>
  Boolean(
    signinCalendar.value?.days.some(
      (day) => day.state === "SIGNED" && day.date.slice(0, 10) === todayIso.value,
    ),
  ),
);

async function loadActivities(): Promise<void> {
  try {
    const result = await fetchActivities();
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      activities.value = [];
      return;
    }
    activities.value = result.data;
  } catch {
    showNetworkFail();
    activities.value = [];
  }
}

async function loadPoints(): Promise<void> {
  try {
    const result = await fetchPointsBalance();
    if (isOk(result) && result.data && result.data.balance != null) {
      pointsBalance.value = Number(result.data.balance);
      pointsGuest.value = false;
      return;
    }
    pointsBalance.value = null;
    pointsGuest.value = isFail(result) && isGuestSessionCode(result.code);
  } catch {
    pointsBalance.value = null;
    pointsGuest.value = false;
  }
}

async function loadTasks(): Promise<void> {
  try {
    const result = await fetchTaskList({ page: 1, pageSize: TODAY_TASK_LIMIT });
    if (isOk(result) && result.data) {
      tasks.value = (result.data.records ?? []).slice(0, TODAY_TASK_LIMIT);
      return;
    }
    tasks.value = [];
  } catch {
    tasks.value = [];
  }
}

async function loadSignin(): Promise<void> {
  try {
    const list = await fetchSigninActivities();
    if (!isOk(list) || !list.data || list.data.length === 0) {
      signinCalendar.value = null;
      signinActivityId.value = null;
      return;
    }
    const id = list.data[0].activityId;
    signinActivityId.value = id;
    const calendar = await fetchSigninCalendar(id);
    if (isOk(calendar) && calendar.data) {
      signinCalendar.value = calendar.data;
      return;
    }
    signinCalendar.value = null;
  } catch {
    signinCalendar.value = null;
    signinActivityId.value = null;
  }
}

async function loadAll(): Promise<void> {
  try {
    await Promise.all([loadActivities(), loadPoints(), loadTasks(), loadSignin()]);
  } finally {
    refreshing.value = false;
    loaded.value = true;
  }
}

function onRefresh(): void {
  void loadAll();
}

function openActivity(activity: PortalActivityView): void {
  void router.push({ path: "/activity", query: { id: String(activity.id) } });
}

function openSignin(): void {
  void router.push("/signin");
}

function openPoints(): void {
  if (pointsGuest.value) {
    void router.push({ path: "/login", query: { redirect: "/home" } });
    return;
  }
  void router.push("/mine/points");
}

function openTaskSheet(task: TaskCardView): void {
  if (task.taskId == null) {
    return;
  }
  sheetTaskId.value = task.taskId;
  sheetOpen.value = true;
}

function coverOf(activity: PortalActivityView): string | undefined {
  return activityCover(activity);
}

function windowOf(activity: PortalActivityView): string {
  return activityWindow(activity.startTime, activity.endTime);
}

async function onHomeCheckin(): Promise<void> {
  if (pointsGuest.value) {
    void router.push({ path: "/login", query: { redirect: "/home" } });
    return;
  }
  if (signinActivityId.value == null || todaySigned.value || signinActing.value) {
    void router.push("/signin");
    return;
  }
  signinActing.value = true;
  try {
    const result = await postCheckin(signinActivityId.value);
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    showSuccessToast(zhCN.signin.checkin);
    await loadSignin();
    await loadPoints();
  } catch {
    showNetworkFail();
  } finally {
    signinActing.value = false;
  }
}

onMounted(() => {
  void loadAll();
});
</script>

<template>
  <section class="home-page">
    <NavBar :title="zhCN.home.title" />
    <AdCarousel position-code="home_banner" />
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <button
        v-if="showPointsBar"
        type="button"
        class="points-bar"
        data-testid="home-points-bar"
        @click="openPoints"
      >
        <span>{{ zhCN.points.balance }}</span>
        <strong v-if="pointsBalance != null" data-testid="home-points-value">{{ pointsBalance }}</strong>
        <span v-else data-testid="home-points-login">{{ zhCN.home.pointsLogin }}</span>
        <span v-if="signinCalendar?.nextRewardHint" class="points-bar__hint">{{ signinCalendar.nextRewardHint }}</span>
      </button>
      <article class="signin-card" data-testid="home-signin-card" @click="openSignin">
        <div class="signin-card__body">
          <strong>{{ zhCN.home.signin }}</strong>
          <span v-if="signinCalendar">
            {{ zhCN.home.streak }} {{ signinCalendar.consecutiveDays }}
          </span>
          <span v-else>{{ zhCN.home.signinHint }}</span>
        </div>
        <ol v-if="signinCalendar" class="signin-week" data-testid="home-signin-week">
          <li
            v-for="cell in week"
            :key="cell.date"
            class="signin-week__cell"
            :class="`signin-week__cell--${cell.state.toLowerCase()}`"
          >
            <span>{{ cell.weekday }}</span>
            <b>{{ cell.dayNum }}</b>
          </li>
        </ol>
        <Button
          type="primary"
          size="small"
          data-testid="home-signin-action"
          :loading="signinActing"
          :disabled="todaySigned"
          @click.stop="onHomeCheckin"
        >
          {{ todaySigned ? zhCN.signin.signed : zhCN.signin.checkin }}
        </Button>
      </article>
      <div v-if="tasks.length" data-testid="home-task-list">
        <h3 class="home-section">{{ zhCN.home.todayTasks }}</h3>
        <TaskCard
          v-for="task in tasks"
          :key="task.taskId"
          :task="task"
          @open="openTaskSheet(task)"
          @action="openTaskSheet(task)"
        />
      </div>
      <h3 v-if="!emptyActivities" class="home-section">{{ zhCN.home.activities }}</h3>
      <Empty v-if="emptyActivities" :description="zhCN.home.empty" data-testid="home-empty">
        <Button type="primary" size="small" data-testid="home-retry" @click="loadActivities">
          {{ zhCN.common.retry }}
        </Button>
      </Empty>
      <div v-else data-testid="home-activity-list">
        <article
          v-for="activity in activities"
          :key="activity.id"
          class="activity-card"
          :data-testid="`home-activity-${activity.id}`"
          role="button"
          tabindex="0"
          @click="openActivity(activity)"
        >
          <div class="activity-card__cover">
            <FallbackImage v-if="coverOf(activity)" :src="coverOf(activity)" :alt="activity.name" />
            <span v-else class="activity-card__fallback">{{ activity.name.slice(0, 1) }}</span>
          </div>
          <span class="activity-card__body">
            <strong>{{ activity.name }}</strong>
            <span v-if="activity.code">{{ activity.code }}</span>
            <span v-if="windowOf(activity)">{{ windowOf(activity) }}</span>
          </span>
        </article>
      </div>
    </PullRefresh>
    <TaskCompleteSheet v-model:show="sheetOpen" :task-id="sheetTaskId" />
  </section>
</template>

<style scoped>
.home-page {
  min-height: 100%;
  padding-bottom: 16px;
  background:
    radial-gradient(120% 70% at 50% -20%, var(--portal-bg-wash) 0%, transparent 58%),
    var(--portal-bg);
}
.home-page :deep(.van-nav-bar) {
  background: transparent;
}
.points-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  width: calc(100% - 32px);
  margin: 12px 16px 0;
  padding: 14px 16px;
  border: 0;
  border-radius: var(--portal-radius);
  background: linear-gradient(135deg, var(--portal-primary-deep) 0%, var(--portal-primary-warm) 100%);
  box-shadow: var(--portal-shadow);
  color: #fff;
  text-align: left;
  font-variant-numeric: tabular-nums;
}
.points-bar span {
  font-size: 13px;
  opacity: 0.9;
}
.points-bar strong {
  font-size: 22px;
  letter-spacing: 0.02em;
}
.points-bar__hint {
  width: 100%;
  margin: 8px 0 0;
  font-size: 12px;
  opacity: 0.92;
}
.signin-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 12px 16px;
  padding: 14px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
  text-align: left;
  cursor: pointer;
}
.signin-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.signin-card__body strong {
  font-size: 16px;
}
.signin-card__body span {
  color: var(--portal-muted);
  font-size: 13px;
}
.signin-week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.signin-week__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 6px 0;
  border-radius: 10px;
  color: var(--portal-muted);
  font-size: 11px;
}
.signin-week__cell b {
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.signin-week__cell--signed,
.signin-week__cell--catchup {
  background: var(--portal-primary-soft);
  color: var(--portal-primary-deep);
}
.signin-week__cell--today_available {
  background: var(--portal-accent-soft);
  color: var(--portal-accent);
}
.home-section {
  margin: 8px 16px 4px;
  font-size: 15px;
  font-weight: 600;
}
.activity-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0 16px 14px;
  border-radius: var(--portal-radius-lg);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow);
  text-align: left;
}
.activity-card__cover {
  display: flex;
  height: 120px;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, var(--portal-primary-warm) 0%, var(--portal-primary-deep) 100%);
}
.activity-card__cover :deep(.fallback-image),
.activity-card__cover :deep(img) {
  width: 100%;
  height: 120px;
  border-radius: 0;
}
.activity-card__fallback {
  color: #fff;
  font-size: 40px;
  font-weight: 700;
}
.activity-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px 16px;
}
.activity-card__body strong {
  font-size: 16px;
}
.activity-card__body span {
  color: var(--portal-muted);
  font-size: 13px;
}
</style>
