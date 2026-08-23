<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useSessionReload } from "@/composables/useSessionReload";
import { useSessionStore } from "@/store/session";
import { Button, Empty, NavBar, showConfirmDialog, showFailToast, showSuccessToast } from "vant";
import { isOk } from "@mkt/shared";
import {
  fetchSigninActivities,
  fetchSigninCalendar,
  postCatchup,
  postCheckin,
  type SigninCalendarResponse,
} from "@/api/signin";
import SigninMonthGrid from "@/components/SigninMonthGrid.vue";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { monthGrid, shiftYearMonth, toIsoDate, yearMonthOf } from "@/utils/home-week";
import { showPortalFail } from "@/utils/portal-error";
import { signinCalendarCell } from "@/utils/signin-calendar-state";

defineOptions({ name: "SigninPage" });

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const calendar = ref<SigninCalendarResponse | null>(null);
const activityId = ref<number | null>(null);
const yearMonth = ref(yearMonthOf(toIsoDate(new Date())));

const todayIso = computed(() => toIsoDate(new Date()));
const todaySigned = computed(() =>
  Boolean(calendar.value?.days.some((day) => day.state === "SIGNED" && normalizeDate(day.date) === todayIso.value)),
);
const insufficient = computed(
  () => calendar.value != null && calendar.value.pointsBalance < calendar.value.catchupCostPoints,
);
const monthCells = computed(() => monthGrid(yearMonth.value, calendar.value?.days ?? []));

function normalizeDate(value: string): string {
  return value.slice(0, 10);
}

async function load(month = yearMonth.value): Promise<void> {
  if (!session.authenticated) {
    loading.value = false;
    calendar.value = null;
    activityId.value = null;
    return;
  }
  loading.value = true;
  const list = await fetchSigninActivities();
  if (!isOk(list) || !list.data || list.data.length === 0) {
    loading.value = false;
    if (!isOk(list)) {
      showPortalFail(list);
    }
    calendar.value = null;
    activityId.value = null;
    return;
  }
  const first = list.data[0];
  activityId.value = first.activityId;
  const result = await fetchSigninCalendar(first.activityId, month);
  loading.value = false;
  if (!isOk(result) || !result.data) {
    showPortalFail(result);
    return;
  }
  calendar.value = result.data;
  yearMonth.value = result.data.yearMonth || month;
  track(TRACK.SIGNIN_PAGE_VIEW, { configId: first.activityId });
}

async function shiftMonth(delta: number): Promise<void> {
  const next = shiftYearMonth(yearMonth.value, delta);
  yearMonth.value = next;
  await load(next);
}

async function onCheckin(): Promise<void> {
  if (activityId.value == null) {
    return;
  }
  track(TRACK.SIGNIN_SIGN_CLICK, { configId: activityId.value });
  const result = await postCheckin(activityId.value);
  if (!isOk(result) || !result.data) {
    showPortalFail(result);
    return;
  }
  showSuccessToast(result.data.alreadySigned ? zhCN.signin.signed : zhCN.signin.checkin);
  await load(yearMonth.value);
}

function catchupBlockedReason(state: string | undefined): string | undefined {
  if (state !== "MISSED_CATCHABLE") {
    return zhCN.signin.notCatchable;
  }
  if (insufficient.value) {
    return zhCN.signin.insufficient;
  }
  return undefined;
}

async function onSelect(date: string): Promise<void> {
  if (activityId.value == null || !calendar.value) {
    return;
  }
  const cell = calendar.value.days.find((day) => normalizeDate(day.date) === date);
  const state =
    cell?.state ??
    signinCalendarCell({
      date,
      today: todayIso.value,
      records: [],
      windowDays: calendar.value.catchupWindowDays,
    });
  if (state === "TODAY_AVAILABLE") {
    await onCheckin();
    return;
  }
  const blocked = catchupBlockedReason(state);
  if (blocked) {
    if (state === "MISSED_CATCHABLE") {
      showFailToast(blocked);
    }
    return;
  }
  try {
    await showConfirmDialog({
      title: zhCN.signin.catchup,
      message: `${zhCN.signin.catchupConfirm}\n${zhCN.signin.cost} ${calendar.value.catchupCostPoints} · ${zhCN.signin.balance} ${calendar.value.pointsBalance}`,
    });
  } catch {
    return;
  }
  track(TRACK.SIGNIN_CATCHUP_CLICK, { configId: activityId.value, signDate: date });
  const result = await postCatchup(activityId.value, date);
  if (!isOk(result) || !result.data) {
    showPortalFail(result);
    return;
  }
  showSuccessToast(zhCN.signin.catchup);
  await load(yearMonth.value);
}

useSessionReload(() => {
  void load(yearMonth.value);
});

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="signin-page">
    <NavBar :title="zhCN.signin.title" left-arrow @click-left="router.back()" />
    <Empty v-if="!loading && !calendar" :description="zhCN.signin.empty" data-testid="signin-empty" />
    <div v-else-if="calendar" class="signin-board" data-testid="signin-calendar">
      <header class="signin-hero">
        <p data-testid="signin-streak">{{ zhCN.signin.streak }} {{ calendar.consecutiveDays }}</p>
        <p v-if="calendar.nextRewardHint" data-testid="signin-hint">{{ calendar.nextRewardHint }}</p>
        <p data-testid="signin-balance">{{ zhCN.signin.balance }} {{ calendar.pointsBalance }} · {{ zhCN.signin.cost }} {{ calendar.catchupCostPoints }}</p>
        <p v-if="insufficient" data-testid="signin-insufficient">{{ zhCN.signin.insufficient }}</p>
      </header>
      <div class="signin-calendar-card">
        <div class="signin-month-nav">
          <Button size="small" data-testid="signin-prev-month" @click="shiftMonth(-1)">{{ zhCN.signin.prevMonth }}</Button>
          <strong data-testid="signin-year-month">{{ yearMonth }}</strong>
          <Button size="small" data-testid="signin-next-month" @click="shiftMonth(1)">{{ zhCN.signin.nextMonth }}</Button>
        </div>
        <SigninMonthGrid :cells="monthCells" @select="onSelect" />
      </div>
      <div class="signin-actions">
        <Button
          type="primary"
          block
          data-testid="signin-checkin"
          :disabled="todaySigned"
          @click="onCheckin"
        >
          {{ todaySigned ? zhCN.signin.signed : zhCN.signin.checkin }}
        </Button>
        <div class="signin-links">
          <Button size="small" data-testid="signin-details" @click="router.push('/mine/points')">
            {{ zhCN.signin.details }}
          </Button>
          <Button size="small" data-testid="signin-prizes" @click="router.push('/mine/prizes')">
            {{ zhCN.mine.prizes }}
          </Button>
        </div>
      </div>
      <section v-if="calendar.tiers.length" class="signin-rewards" data-testid="signin-rewards">
        <h3>{{ zhCN.signin.rewards }}</h3>
        <button
          v-for="tier in calendar.tiers"
          :key="tier.day"
          type="button"
          class="signin-reward"
          data-testid="signin-reward-row"
          @click="router.push('/mine/prizes')"
        >
          {{ zhCN.signin.rewardDay }} {{ tier.day }}{{ zhCN.home.dayUnit }}
        </button>
      </section>
    </div>
  </section>
</template>

<style scoped>
.signin-page {
  min-height: 100%;
  background:
    radial-gradient(120% 50% at 50% -10%, var(--portal-bg-wash) 0%, transparent 50%),
    var(--portal-bg);
}
.signin-page :deep(.van-nav-bar) {
  background: transparent;
}
.signin-hero {
  margin: 8px 16px 12px;
  padding: 18px 16px;
  border-radius: var(--portal-radius-lg);
  background: linear-gradient(135deg, var(--portal-primary-deep) 0%, var(--portal-primary-warm) 100%);
  box-shadow: var(--portal-shadow);
  color: #fff;
}
.signin-hero p {
  margin: 0 0 6px;
  font-size: 13px;
  opacity: 0.95;
}
.signin-hero p:first-child {
  font-size: 22px;
  font-weight: 700;
  opacity: 1;
}
.signin-hero p:last-child {
  margin-bottom: 0;
}
.signin-calendar-card {
  overflow: hidden;
  margin: 0 16px 12px;
  padding: 12px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.signin-month-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.signin-actions {
  padding: 4px 16px 12px;
}
.signin-links {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.signin-rewards {
  margin: 0 16px 24px;
  padding: 12px 14px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.signin-rewards h3 {
  margin: 0 0 8px;
  font-size: 14px;
}
.signin-reward {
  display: block;
  width: 100%;
  margin: 0 0 6px;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: var(--portal-primary-soft);
  color: var(--portal-primary-deep);
  text-align: left;
  font-size: 13px;
}
</style>
