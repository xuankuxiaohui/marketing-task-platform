<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useSessionReload } from "@/composables/useSessionReload";
import { Button, Empty, NavBar, PullRefresh, showSuccessToast } from "vant";
import { isOk } from "@mkt/shared";
import { fetchActivities, type PortalActivityView } from "@/api/activity";
import { fetchPointsBalance } from "@/api/points";
import {
  fetchSigninActivities,
  fetchSigninCalendar,
  postCheckin,
  type SigninCalendarResponse,
} from "@/api/signin";
import AdCarousel from "@/components/AdCarousel.vue";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { activityCover } from "@/utils/activity-cover";
import { toIsoDate } from "@/utils/home-week";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";

defineOptions({ name: "HomePage" });

const router = useRouter();
const session = useSessionStore();
const overlay = useLoginOverlayStore();
const activities = ref<PortalActivityView[]>([]);
const refreshing = ref(false);
const loaded = ref(false);
const pointsBalance = ref<number | null>(null);
const signinCalendar = ref<SigninCalendarResponse | null>(null);
const signinActivityId = ref<number | null>(null);
const signinActing = ref(false);

const emptyActivities = computed(() => loaded.value && activities.value.length === 0);
const todayIso = computed(() => toIsoDate(new Date()));
const todaySigned = computed(() =>
  Boolean(
    signinCalendar.value?.days.some(
      (day) => day.state === "SIGNED" && day.date.slice(0, 10) === todayIso.value,
    ),
  ),
);
const streakDays = computed(() => signinCalendar.value?.consecutiveDays ?? null);

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
  if (!session.authenticated) {
    pointsBalance.value = null;
    return;
  }
  try {
    const result = await fetchPointsBalance();
    if (isOk(result) && result.data && result.data.balance != null) {
      pointsBalance.value = Number(result.data.balance);
      return;
    }
    pointsBalance.value = null;
  } catch {
    pointsBalance.value = null;
  }
}

async function loadSignin(): Promise<void> {
  if (!session.authenticated) {
    signinCalendar.value = null;
    signinActivityId.value = null;
    return;
  }
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
    const jobs: Promise<void>[] = [loadActivities()];
    if (session.authenticated) {
      jobs.push(loadPoints(), loadSignin());
    } else {
      pointsBalance.value = null;
      signinCalendar.value = null;
      signinActivityId.value = null;
    }
    await Promise.all(jobs);
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

function coverOf(activity: PortalActivityView): string | undefined {
  return activityCover(activity);
}

function requestHomeLogin(resume?: () => void, redirect = "/home"): void {
  overlay.request({ redirect, resume });
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

async function onHomeCheckin(): Promise<void> {
  if (!session.authenticated) {
    requestHomeLogin(() => {
      void onHomeCheckin();
    });
    return;
  }
  if (signinActivityId.value == null) {
    await loadSignin();
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

useSessionReload(() => {
  void Promise.all([loadPoints(), loadSignin()]);
});

onMounted(() => {
  void loadAll();
});
</script>

<template>
  <section class="home-page">
    <NavBar :title="zhCN.home.title" />
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <article class="signin-strip" data-testid="home-signin-card">
        <button type="button" class="signin-strip__meta" data-testid="home-points-bar" @click="openPoints">
          <strong>{{ zhCN.home.signin }}</strong>
          <span>
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
      </article>
      <AdCarousel position-code="home_banner" />
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
          class="activity-banner"
          :data-testid="`home-activity-${activity.id}`"
          role="button"
          tabindex="0"
          @click="openActivity(activity)"
        >
          <div class="activity-banner__cover">
            <FallbackImage v-if="coverOf(activity)" :src="coverOf(activity)" :alt="activity.name" />
            <span v-else class="activity-banner__fallback">{{ activity.name.slice(0, 1) }}</span>
          </div>
          <span class="activity-banner__title">{{ activity.name }}</span>
        </article>
      </div>
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
.signin-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 10px 16px 0;
  padding: 10px 12px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.signin-strip__meta {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  color: inherit;
}
.signin-strip__meta strong {
  font-size: 14px;
}
.signin-strip__meta span {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--portal-muted);
  font-size: 12px;
}
.signin-strip__meta b {
  margin-left: 4px;
  color: var(--portal-ink);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.signin-strip__meta em {
  margin-left: 4px;
  font-style: normal;
}
.home-section {
  margin: 8px 16px 4px;
  font-size: 15px;
  font-weight: 600;
}
.activity-banner {
  position: relative;
  overflow: hidden;
  margin: 0 16px 12px;
  border-radius: var(--portal-radius-lg);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow);
  text-align: left;
}
.activity-banner__cover {
  display: flex;
  height: 148px;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, var(--portal-primary-warm) 0%, var(--portal-primary-deep) 100%);
}
.activity-banner__cover :deep(.fallback-image),
.activity-banner__cover :deep(img) {
  width: 100%;
  height: 148px;
  border-radius: 0;
  object-fit: cover;
}
.activity-banner__fallback {
  color: #fff;
  font-size: 40px;
  font-weight: 700;
}
.activity-banner__title {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 28px 14px 12px;
  background: linear-gradient(180deg, transparent 0%, rgba(28, 25, 23, 0.72) 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}
</style>
