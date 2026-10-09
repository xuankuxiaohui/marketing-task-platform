<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { useLatestRequest } from "@/composables/useLatestRequest";
import { Button, Empty, NavBar, PullRefresh, showSuccessToast } from "vant";
import { isOk } from "@mkt/shared";
import { fetchActivities, type PortalActivityView } from "@/api/activity";
import { fetchPointsBalance } from "@/api/points";
import {
  fetchSigninActivities,
  fetchSigninCalendar,
  postCheckin,
  type CalendarDayView,
} from "@/api/signin";
import AdCarousel from "@/components/AdCarousel.vue";
import FallbackImage from "@/components/FallbackImage.vue";
import SigninWeekRow from "@/components/SigninWeekRow.vue";
import { zhCN } from "@/locales/zh-CN";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { activityCover, activityWindow } from "@/utils/activity-cover";
import {
  shiftWeek,
  signinWeek,
  toIsoDate,
  weekStartSunday,
  yearMonthOf,
} from "@/utils/home-week";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";

defineOptions({ name: "HomePage" });

const router = useRouter();
const session = useSessionStore();
const overlay = useLoginOverlayStore();
const refreshing = ref(false);
const pointsBalance = ref<number | null>(null);
const signinDays = ref<CalendarDayView[]>([]);
const consecutiveDays = ref<number | null>(null);
const signinActivityId = ref<number | null>(null);
const signinActing = ref(false);
const loadedMonths = ref(new Set<string>());
const weekStart = ref(weekStartSunday(toIsoDate(new Date())));
const activities = ref<PortalActivityView[]>([]);
const activitiesLoaded = ref(false);
const requestScope = () => session.token;
const beginPointsRequest = useLatestRequest(requestScope);
const beginSigninRequest = useLatestRequest(requestScope);
const beginActivitiesRequest = useLatestRequest(requestScope);
const beginRefreshRequest = useLatestRequest(requestScope);
const beginCheckinRequest = useLatestRequest(requestScope);

const todayIso = computed(() => toIsoDate(new Date()));
const currentWeekStart = computed(() => weekStartSunday(todayIso.value));
const canNextWeek = computed(() => weekStart.value < currentWeekStart.value);
const todaySigned = computed(() =>
  Boolean(signinDays.value.some((day) => day.state === "SIGNED" && day.date.slice(0, 10) === todayIso.value)),
);
const streakDays = computed(() => consecutiveDays.value);
const weekCells = computed(() => signinWeek(signinDays.value, weekStart.value));
const emptyActivities = computed(() => activitiesLoaded.value && activities.value.length === 0);

function resetSignin(): void {
  signinDays.value = [];
  consecutiveDays.value = null;
  signinActivityId.value = null;
  loadedMonths.value = new Set();
}

function resetHome(): void {
  pointsBalance.value = null;
  resetSignin();
  activities.value = [];
  activitiesLoaded.value = false;
  refreshing.value = false;
  signinActing.value = false;
}

async function loadPoints(): Promise<void> {
  const isCurrent = beginPointsRequest();
  if (!isCurrent()) {
    return;
  }
  if (!session.authenticated) {
    pointsBalance.value = null;
    return;
  }
  try {
    const result = await fetchPointsBalance();
    if (!isCurrent()) {
      return;
    }
    if (isOk(result) && result.data && result.data.balance != null) {
      pointsBalance.value = Number(result.data.balance);
      return;
    }
    pointsBalance.value = null;
  } catch {
    if (isCurrent()) {
      pointsBalance.value = null;
    }
  }
}

async function ensureCalendarMonth(activityId: number, yearMonth: string, isCurrent: () => boolean): Promise<void> {
  if (loadedMonths.value.has(yearMonth)) {
    return;
  }
  const calendar = await fetchSigninCalendar(activityId, yearMonth);
  if (!isCurrent() || !isOk(calendar) || !calendar.data) {
    return;
  }
  consecutiveDays.value = calendar.data.consecutiveDays ?? consecutiveDays.value;
  const next = new Map(signinDays.value.map((day) => [day.date.slice(0, 10), day]));
  for (const day of calendar.data.days ?? []) {
    next.set(day.date.slice(0, 10), day);
  }
  signinDays.value = [...next.values()];
  loadedMonths.value = new Set([...loadedMonths.value, yearMonth]);
}

async function loadWeekMonths(isCurrent: () => boolean): Promise<void> {
  const activityId = signinActivityId.value;
  if (!isCurrent() || activityId == null) {
    return;
  }
  const months = new Set([yearMonthOf(weekStart.value), yearMonthOf(shiftWeek(weekStart.value, 1))]);
  for (const month of months) {
    if (!isCurrent()) {
      return;
    }
    await ensureCalendarMonth(activityId, month, isCurrent);
  }
}

async function loadSignin(): Promise<void> {
  const isCurrent = beginSigninRequest();
  if (!isCurrent()) {
    return;
  }
  resetSignin();
  if (!session.authenticated) {
    return;
  }
  try {
    const list = await fetchSigninActivities();
    if (!isCurrent()) {
      return;
    }
    if (!isOk(list) || !list.data || list.data.length === 0) {
      return;
    }
    const id = list.data[0].activityId;
    signinActivityId.value = id;
    await loadWeekMonths(isCurrent);
  } catch {
    if (isCurrent()) {
      resetSignin();
    }
  }
}

async function loadActivities(): Promise<void> {
  const isCurrent = beginActivitiesRequest();
  if (!isCurrent()) {
    return;
  }
  try {
    const result = await fetchActivities();
    if (isCurrent()) {
      activities.value = isOk(result) && result.data ? result.data : [];
    }
  } catch {
    if (isCurrent()) {
      activities.value = [];
    }
  } finally {
    if (isCurrent()) {
      activitiesLoaded.value = true;
    }
  }
}

async function loadAll(): Promise<void> {
  const isCurrent = beginRefreshRequest();
  if (!isCurrent()) {
    return;
  }
  try {
    await Promise.all([loadActivities(), loadPoints(), loadSignin()]);
  } finally {
    if (isCurrent()) {
      refreshing.value = false;
    }
  }
}

function onRefresh(): void {
  void loadAll();
}

function requestHomeLogin(resume?: () => void, redirect = "/home"): void {
  overlay.request({ redirect, resume });
}

function openSigninPage(): void {
  if (!session.authenticated) {
    requestHomeLogin(() => {
      void router.push("/signin");
    }, "/signin");
    return;
  }
  void router.push("/signin");
}

function openPoints(): void {
  if (!session.authenticated) {
    requestHomeLogin(() => {
      void router.push("/mine/points");
    }, "/mine/points");
    return;
  }
  void router.push("/mine/points");
}

async function shiftHomeWeek(delta: number): Promise<void> {
  const next = shiftWeek(weekStart.value, delta);
  if (delta > 0 && next > currentWeekStart.value) {
    return;
  }
  weekStart.value = next;
  if (session.authenticated && signinActivityId.value != null) {
    const isCurrent = beginSigninRequest();
    try {
      await loadWeekMonths(isCurrent);
    } catch {
      if (isCurrent()) {
        showNetworkFail();
      }
    }
  }
}

async function onHomeCheckin(): Promise<void> {
  if (signinActing.value) {
    return;
  }
  const isCurrent = beginCheckinRequest();
  if (!isCurrent()) {
    return;
  }
  if (!session.authenticated) {
    requestHomeLogin(() => {
      void onHomeCheckin();
    });
    return;
  }
  if (signinActivityId.value == null) {
    await loadSignin();
  }
  if (!isCurrent()) {
    return;
  }
  if (signinActivityId.value == null || todaySigned.value) {
    void router.push("/signin");
    return;
  }
  signinActing.value = true;
  try {
    const result = await postCheckin(signinActivityId.value);
    if (!isCurrent()) {
      return;
    }
    if (!isOk(result) || !result.data) {
      showPortalFail(result);
      return;
    }
    showSuccessToast(zhCN.signin.checkin);
    await Promise.all([loadSignin(), loadPoints()]);
  } catch {
    if (isCurrent()) {
      showNetworkFail();
    }
  } finally {
    if (isCurrent()) {
      signinActing.value = false;
    }
  }
}

function openActivity(row: PortalActivityView): void {
  void router.push({ path: "/activity", query: { id: String(row.id) } });
}

watch(requestScope, () => {
  resetHome();
  void loadAll();
}, { immediate: true, flush: "sync" });
</script>

<template>
  <section class="home-page">
    <NavBar :title="zhCN.home.title" />
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <article class="signin-panel" data-testid="home-signin-card">
        <header class="signin-panel__head">
          <strong>{{ zhCN.home.signin }}</strong>
          <span class="signin-panel__week-nav">
            <button type="button" data-testid="home-week-prev" @click="shiftHomeWeek(-1)">
              {{ zhCN.home.prevWeek }}
            </button>
            <button type="button" data-testid="home-week-next" :disabled="!canNextWeek" @click="shiftHomeWeek(1)">
              {{ zhCN.home.nextWeek }}
            </button>
          </span>
        </header>
        <div class="signin-panel__cal" data-testid="home-signin-calendar">
          <SigninWeekRow :cells="weekCells" @select="openSigninPage" />
        </div>
        <div class="signin-panel__stats">
          <button type="button" class="signin-panel__meta" data-testid="home-points-bar" @click="openPoints">
            <span data-testid="home-signin-balance">
              {{ zhCN.points.balance }}
              <b v-if="pointsBalance != null" data-testid="home-points-value">{{ pointsBalance }}</b>
              <em v-else data-testid="home-points-login">{{ zhCN.home.pointsLogin }}</em>
            </span>
            <span data-testid="home-signin-streak">
              {{ zhCN.home.streak }}
              <template v-if="streakDays != null"> {{ streakDays }}{{ zhCN.home.dayUnit }}</template>
              <template v-else> {{ zhCN.home.pointsLogin }}</template>
            </span>
          </button>
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
        </div>
      </article>
      <AdCarousel position-code="home_banner" />
      <section class="home-activities">
        <h2>{{ zhCN.home.activities }}</h2>
        <Empty
          v-if="emptyActivities"
          :description="zhCN.home.empty"
          data-testid="home-empty"
        />
        <div v-else-if="activities.length > 0" data-testid="home-activity-list">
          <button
            v-for="row in activities"
            :key="row.id"
            type="button"
            class="home-activity"
            :data-testid="`home-activity-${row.id}`"
            @click="openActivity(row)"
          >
            <FallbackImage v-if="activityCover(row)" :src="activityCover(row)" :alt="row.name" />
            <span v-else class="home-activity__fallback">{{ row.name.slice(0, 1) }}</span>
            <span class="home-activity__meta">
              <strong>{{ row.name }}</strong>
              <em v-if="activityWindow(row.startTime, row.endTime)">{{
                activityWindow(row.startTime, row.endTime)
              }}</em>
            </span>
          </button>
        </div>
      </section>
    </PullRefresh>
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
.signin-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 10px 16px 0;
  padding: 12px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.signin-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.signin-panel__head strong {
  font-size: 14px;
}
.signin-panel__week-nav {
  display: flex;
  gap: 8px;
}
.signin-panel__week-nav button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--portal-primary);
  font-size: 12px;
}
.signin-panel__week-nav button:disabled {
  color: var(--portal-muted);
}
.signin-panel__cal {
  width: 100%;
}
.signin-panel__stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.signin-panel__meta {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  color: var(--portal-muted);
  font-size: 12px;
}
.signin-panel__meta b {
  margin-left: 4px;
  color: var(--portal-ink);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.signin-panel__meta em {
  margin-left: 4px;
  font-style: normal;
}
.home-activities {
  margin: 16px 16px 0;
}
.home-activities h2 {
  margin: 0 0 10px;
  font-size: 15px;
}
.home-activity {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  margin: 0 0 12px;
  padding: 12px;
  border: 0;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
  text-align: left;
}
.home-activity :deep(.fallback-image),
.home-activity__fallback {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 14px;
  background: var(--portal-primary-soft);
}
.home-activity__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--portal-primary);
  font-size: 20px;
  font-weight: 700;
}
.home-activity__meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}
.home-activity__meta strong {
  font-size: 15px;
}
.home-activity__meta em {
  color: var(--portal-muted);
  font-size: 12px;
  font-style: normal;
}
</style>
