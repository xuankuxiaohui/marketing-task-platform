<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useSessionReload } from "@/composables/useSessionReload";
import { Button, NavBar, PullRefresh, showSuccessToast } from "vant";
import { isOk } from "@mkt/shared";
import { fetchPointsBalance } from "@/api/points";
import {
  fetchSigninActivities,
  fetchSigninCalendar,
  postCheckin,
  type SigninCalendarResponse,
} from "@/api/signin";
import AdCarousel from "@/components/AdCarousel.vue";
import SigninMonthGrid from "@/components/SigninMonthGrid.vue";
import { zhCN } from "@/locales/zh-CN";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { monthGrid, toIsoDate, yearMonthOf } from "@/utils/home-week";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";

defineOptions({ name: "HomePage" });

const router = useRouter();
const session = useSessionStore();
const overlay = useLoginOverlayStore();
const refreshing = ref(false);
const pointsBalance = ref<number | null>(null);
const signinCalendar = ref<SigninCalendarResponse | null>(null);
const signinActivityId = ref<number | null>(null);
const signinActing = ref(false);

const todayIso = computed(() => toIsoDate(new Date()));
const todaySigned = computed(() =>
  Boolean(
    signinCalendar.value?.days.some(
      (day) => day.state === "SIGNED" && day.date.slice(0, 10) === todayIso.value,
    ),
  ),
);
const streakDays = computed(() => signinCalendar.value?.consecutiveDays ?? null);
const monthCells = computed(() =>
  monthGrid(signinCalendar.value?.yearMonth ?? yearMonthOf(todayIso.value), signinCalendar.value?.days ?? []),
);

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
    if (session.authenticated) {
      await Promise.all([loadPoints(), loadSignin()]);
    } else {
      pointsBalance.value = null;
      signinCalendar.value = null;
      signinActivityId.value = null;
    }
  } finally {
    refreshing.value = false;
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
      <article class="signin-panel" data-testid="home-signin-card">
        <div class="signin-panel__info">
          <strong>{{ zhCN.home.signin }}</strong>
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
        <div
          class="signin-panel__cal"
          data-testid="home-signin-calendar"
          role="button"
          tabindex="0"
          @click="openSigninPage"
        >
          <SigninMonthGrid :cells="monthCells" compact @select="openSigninPage" />
        </div>
      </article>
      <AdCarousel position-code="home_banner" />
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
  gap: 10px;
  align-items: stretch;
  margin: 10px 16px 0;
  padding: 10px 12px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.signin-panel__info {
  display: flex;
  min-width: 0;
  flex: 1 1 70%;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}
.signin-panel__info strong {
  font-size: 14px;
}
.signin-panel__meta {
  display: flex;
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
.signin-panel__cal {
  flex: 0 0 30%;
  width: 30%;
  max-width: 30%;
  padding: 0;
  border: 0;
  background: transparent;
}
</style>
