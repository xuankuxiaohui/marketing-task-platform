<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Empty, List, NavBar, PullRefresh } from "vant";
import { isOk } from "@mkt/shared";
import { fetchPointsBalance, fetchPointsTransactions, type PointsPortalTxView } from "@/api/points";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { TRACK, track } from "@/tracking";
import { formatBeijing } from "@/utils/datetime";
import { showNetworkFail, showPortalFail } from "@/utils/portal-error";

defineOptions({ name: "MinePointsPage" });

const PAGE_SIZE = 20;

const TYPE_LABEL: Record<string, string> = {
  EARN: zhCN.points.earn,
  CONSUME: zhCN.points.consume,
  EXPIRE: zhCN.points.expire,
  ADJUST: zhCN.points.adjust,
  REVERSAL: zhCN.points.reversal,
};

const router = useRouter();
const session = useSessionStore();
const balance = ref<number>(session.pointsBalance);
const records = ref<PointsPortalTxView[]>([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const loaded = ref(false);

const empty = computed(() => loaded.value && records.value.length === 0);

function typeLabel(type?: string): string {
  if (!type) {
    return "";
  }
  return TYPE_LABEL[type] ?? type;
}

function formatAmount(amount?: number): string {
  const value = Number(amount ?? 0);
  if (value > 0) {
    return `+${value}`;
  }
  return String(value);
}

async function loadBalance(): Promise<void> {
  const result = await fetchPointsBalance();
  if (!isOk(result) || !result.data) {
    showPortalFail(result);
    return;
  }
  const next = Number(result.data.balance ?? 0);
  balance.value = next;
  session.setPointsBalance(next);
}

async function loadPage(reset: boolean): Promise<void> {
  if (reset) {
    page.value = 1;
    finished.value = false;
  }
  loading.value = true;
  try {
    const result = await fetchPointsTransactions({ page: page.value, pageSize: PAGE_SIZE });
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

async function onRefresh(): Promise<void> {
  try {
    await loadBalance();
  } catch {
    showNetworkFail();
  }
  await loadPage(true);
}

function onLoadMore(): void {
  if (refreshing.value || loading.value) {
    return;
  }
  void loadPage(false);
}

function openSource(row: PointsPortalTxView): void {
  if (row.sourceTaskId == null) {
    return;
  }
  void router.push(`/task/${row.sourceTaskId}`);
}

onMounted(() => {
  track(TRACK.POINTS_PAGE_VIEW);
  void loadBalance().catch(() => {
    showNetworkFail();
  });
  void loadPage(true);
});
</script>

<template>
  <section class="mine-points">
    <NavBar :title="zhCN.mine.pointsDetail" left-arrow @click-left="router.back()" />
    <div class="points-balance" data-testid="points-balance">
      <span>{{ zhCN.points.balance }}</span>
      <strong>{{ balance }}</strong>
    </div>
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <Empty v-if="empty" :description="zhCN.empty.points" data-testid="mine-points-empty" />
      <List
        v-else
        v-model:loading="loading"
        :finished="finished"
        :finished-text="zhCN.task.noMore"
        :immediate-check="false"
        data-testid="mine-points-list"
        @load="onLoadMore"
      >
        <button
          v-for="(row, index) in records"
          :key="`${row.createdAt}-${row.type}-${row.amount}-${row.balanceAfter}-${index}`"
          class="points-row"
          type="button"
          data-testid="points-row"
          :disabled="row.sourceTaskId == null"
          @click="openSource(row)"
        >
          <span class="points-row__main">
            <strong>{{ typeLabel(row.type) }}</strong>
            <span v-if="row.remark">{{ row.remark }}</span>
            <span v-if="row.createdAt">{{ formatBeijing(row.createdAt) }}</span>
            <span v-if="row.sourceTaskId != null" data-testid="points-source">
              {{ zhCN.prize.source }} {{ row.sourceTaskId }}
            </span>
          </span>
          <span class="points-row__amount">
            <strong :data-sign="Number(row.amount ?? 0) >= 0 ? 'plus' : 'minus'">{{ formatAmount(row.amount) }}</strong>
            <span>{{ row.balanceAfter }}</span>
          </span>
        </button>
      </List>
    </PullRefresh>
  </section>
</template>

<style scoped>
.points-balance {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 12px 16px;
  padding: 20px 16px;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  box-shadow: var(--portal-shadow-soft);
  font-variant-numeric: tabular-nums;
}
.points-balance span {
  color: var(--portal-muted);
  font-size: 13px;
}
.points-balance strong {
  font-size: 32px;
}
.points-row {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  width: calc(100% - 32px);
  margin: 0 16px 12px;
  padding: 12px;
  border: 0;
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
  text-align: left;
}
.points-row:disabled {
  color: inherit;
}
.points-row__main,
.points-row__amount {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.points-row__amount {
  align-items: flex-end;
}
.points-row__main span,
.points-row__amount span {
  color: var(--portal-muted);
  font-size: 12px;
}
.points-row__amount strong[data-sign="plus"] {
  color: var(--portal-primary);
}
.points-row__amount strong[data-sign="minus"] {
  color: var(--portal-accent);
}
</style>
