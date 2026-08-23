<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useSessionReload } from "@/composables/useSessionReload";
import { useSessionStore } from "@/store/session";
import { Button, Calendar, Empty, NavBar, showConfirmDialog, showFailToast, showSuccessToast, type CalendarDayItem } from "vant";
import { isOk } from "@mkt/shared";
import {
  fetchSigninActivities,
  fetchSigninCalendar,
  postCatchup,
  postCheckin,
  type CalendarDayView,
  type SigninCalendarResponse,
} from "@/api/signin";
import { zhCN } from "@/locales/zh-CN";
import { TRACK, track } from "@/tracking";
import { showPortalFail } from "@/utils/portal-error";
import { signinCalendarCell } from "@/utils/signin-calendar-state";

defineOptions({ name: "SigninPage" });

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const calendar = ref<SigninCalendarResponse | null>(null);
const activityId = ref<number | null>(null);

const todayIso = computed(() => toIsoDate(new Date()));
const todaySigned = computed(() =>
  Boolean(calendar.value?.days.some((day) => day.state === "SIGNED" && normalizeDate(day.date) === todayIso.value)),
);
const insufficient = computed(
  () => calendar.value != null && calendar.value.pointsBalance < calendar.value.catchupCostPoints,
);

const formatter = computed(() => {
  const days = calendar.value?.days ?? [];
  const byDate = new Map(days.map((day) => [normalizeDate(day.date), day]));
  return (day: CalendarDayItem): CalendarDayItem => {
    if (!day.date) {
      return day;
    }
    const key = toIsoDate(day.date);
    const cell = byDate.get(key);
    return {
      ...day,
      className: cellClass(cell),
      text: String(day.date.getDate()),
    };
  };
});

function normalizeDate(value: string): string {
  return value.slice(0, 10);
}

function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function cellClass(cell: CalendarDayView | undefined): string {
  switch (cell?.state) {
    case "SIGNED":
      return "signin-signed";
    case "CATCHUP":
      return "signin-catchup";
    case "MISSED_CATCHABLE":
      return "signin-missed";
    case "TODAY_AVAILABLE":
      return "signin-today";
    default:
      return "";
  }
}

async function load(): Promise<void> {
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
  const result = await fetchSigninCalendar(first.activityId);
  loading.value = false;
  if (!isOk(result) || !result.data) {
    showPortalFail(result);
    return;
  }
  calendar.value = result.data;
  track(TRACK.SIGNIN_PAGE_VIEW, { configId: first.activityId });
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
  await load();
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

async function onSelect(value: Date): Promise<void> {
  if (activityId.value == null || !calendar.value) {
    return;
  }
  const date = toIsoDate(value);
  const cell = calendar.value.days.find((day) => normalizeDate(day.date) === date);
  const state = cell?.state
    ?? signinCalendarCell({
      date,
      today: toIsoDate(new Date()),
      records: [],
      windowDays: calendar.value.catchupWindowDays,
    });
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
  await load();
}

useSessionReload(() => {
  void load();
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
        <Calendar
          :poppable="false"
          :show-title="false"
          :show-confirm="false"
          :formatter="formatter"
          @select="onSelect"
        />
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
      </div>
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
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
}
.signin-actions {
  position: sticky;
  bottom: 8px;
  padding: 4px 16px 24px;
}
:deep(.signin-signed) {
  color: var(--portal-primary);
  font-weight: 600;
}
:deep(.signin-catchup) {
  color: var(--portal-primary-warm);
  font-weight: 600;
}
:deep(.signin-missed) {
  color: var(--portal-accent);
}
:deep(.signin-today) {
  color: var(--van-warning-color);
  font-weight: 600;
}
</style>
