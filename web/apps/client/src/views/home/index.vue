<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Button, Empty, NavBar, PullRefresh } from "vant";
import { isFail, isOk } from "@mkt/shared";
import { fetchActivities, type PortalActivityView } from "@/api/activity";
import { fetchPointsBalance } from "@/api/points";
import AdCarousel from "@/components/AdCarousel.vue";
import FallbackImage from "@/components/FallbackImage.vue";
import { zhCN } from "@/locales/zh-CN";
import { activityCover, activityWindow } from "@/utils/activity-cover";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";
import { isGuestSessionCode } from "@/utils/session-reason";

defineOptions({ name: "HomePage" });

const router = useRouter();
const activities = ref<PortalActivityView[]>([]);
const refreshing = ref(false);
const loaded = ref(false);
const pointsBalance = ref<number | null>(null);
const pointsGuest = ref(false);

const empty = computed(() => loaded.value && activities.value.length === 0);
const showPointsBar = computed(() => pointsBalance.value != null || pointsGuest.value);

async function load(): Promise<void> {
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
  } finally {
    refreshing.value = false;
    loaded.value = true;
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

function onRefresh(): void {
  void load();
  void loadPoints();
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

function coverOf(activity: PortalActivityView): string | undefined {
  return activityCover(activity);
}

function windowOf(activity: PortalActivityView): string {
  return activityWindow(activity.startTime, activity.endTime);
}

onMounted(() => {
  void load();
  void loadPoints();
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
      </button>
      <article
        class="signin-card"
        data-testid="home-signin-card"
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
      <Empty v-if="empty" :description="zhCN.home.empty" data-testid="home-empty">
        <Button type="primary" size="small" data-testid="home-retry" @click="load">
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
}
.points-bar span {
  font-size: 13px;
  opacity: 0.9;
}
.points-bar strong {
  font-size: 22px;
  letter-spacing: 0.02em;
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
