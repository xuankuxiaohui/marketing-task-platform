<script setup lang="ts">
import { onMounted, onScopeDispose, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, DropdownItem, DropdownMenu, Empty, List, NavBar, PullRefresh } from "vant";
import { isOk } from "@mkt/shared";
import { fetchPointsBalance, fetchPointsTransactions, type PointsPortalTxView } from "@/api/points";
import { usePagedList } from "@/composables/usePagedList";
import { zhCN } from "@/locales/zh-CN";
import { useLoginOverlayStore } from "@/store/login-overlay";
import { useSessionStore } from "@/store/session";
import { TRACK, track } from "@/tracking";
import { formatBeijing } from "@/utils/datetime";
import { resultMessage } from "@/utils/portal-error";

defineOptions({ name: "MinePointsPage" });

const TYPE_LABEL: Record<string, string> = {
  EARN: zhCN.points.earn,
  CONSUME: zhCN.points.consume,
  EXPIRE: zhCN.points.expire,
  ADJUST: zhCN.points.adjust,
  REVERSAL: zhCN.points.reversal,
};

const TYPE_OPTIONS = [
  { text: zhCN.points.allTypes, value: "" },
  ...Object.entries(TYPE_LABEL).map(([value, text]) => ({ text, value })),
];

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const overlay = useLoginOverlayStore();
const activeType = ref("");
const balance = ref(0);
const balanceLoading = ref(false);
const balanceError = ref("");
const refreshing = ref(false);
const { records, loading, finished, error, errorMessage, empty, loadMore, refresh, retry } =
  usePagedList<PointsPortalTxView>({
    scope: () => [session.token, activeType.value],
    enabled: () => session.authenticated,
    fetchPage: (page, pageSize) => fetchPointsTransactions({
      type: activeType.value || undefined,
      page,
      pageSize,
    }),
  });
let balanceGeneration = 0;
let refreshGeneration = 0;
let disposed = false;
let nextRowKey = 0;
const rowKeys = new WeakMap<PointsPortalTxView, number>();

// The ledger contract has no row ID. Object keys remain stable when pages append,
// and distinguish separate transactions whose displayed fields happen to match.
function rowKey(row: PointsPortalTxView): number {
  let key = rowKeys.get(row);
  if (key == null) {
    key = ++nextRowKey;
    rowKeys.set(row, key);
  }
  return key;
}

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
  const generation = ++balanceGeneration;
  const token = session.token;
  balanceError.value = "";
  if (!session.authenticated) {
    balance.value = 0;
    balanceLoading.value = false;
    return;
  }
  balanceLoading.value = true;
  const current = () => !disposed && generation === balanceGeneration && session.token === token;
  try {
    const result = await fetchPointsBalance();
    if (!current()) {
      return;
    }
    if (!isOk(result) || !result.data) {
      balanceError.value = resultMessage(result);
      return;
    }
    const next = Number(result.data.balance ?? 0);
    balance.value = next;
    session.setPointsBalance(next);
  } catch {
    if (current()) {
      balanceError.value = zhCN.common.networkError;
    }
  } finally {
    if (current()) {
      balanceLoading.value = false;
    }
  }
}

async function onRefresh(): Promise<void> {
  const generation = ++refreshGeneration;
  const token = session.token;
  refreshing.value = true;
  try {
    await Promise.all([loadBalance(), refresh()]);
  } finally {
    if (!disposed && generation === refreshGeneration && session.token === token) {
      refreshing.value = false;
    }
  }
}

function requestLogin(): void {
  overlay.request({ redirect: route.fullPath });
}

function openSource(row: PointsPortalTxView): void {
  if (row.sourceTaskId == null) {
    return;
  }
  void router.push(`/task/${row.sourceTaskId}`);
}

watch(() => session.token, () => {
  refreshGeneration += 1;
  refreshing.value = false;
  balance.value = 0;
  void loadBalance();
}, { immediate: true, flush: "sync" });

onScopeDispose(() => {
  disposed = true;
  balanceGeneration += 1;
  refreshGeneration += 1;
});

onMounted(() => {
  track(TRACK.POINTS_PAGE_VIEW);
});
</script>

<template>
  <section class="mine-points">
    <NavBar :title="zhCN.mine.pointsDetail" left-arrow @click-left="router.back()" />
    <div class="points-balance" data-testid="points-balance">
      <span>{{ zhCN.points.balance }}</span>
      <span v-if="!session.authenticated">{{ zhCN.home.pointsLogin }}</span>
      <span v-else-if="balanceLoading" data-testid="points-balance-loading">{{ zhCN.common.loading }}</span>
      <div v-else-if="balanceError" role="alert" data-testid="points-balance-error">
        <span>{{ balanceError }}</span>
        <Button size="small" data-testid="points-balance-retry" @click="loadBalance">
          {{ zhCN.common.retry }}
        </Button>
      </div>
      <strong v-else data-testid="points-balance-amount">{{ balance }}</strong>
    </div>
    <DropdownMenu v-if="session.authenticated" data-testid="points-types">
      <DropdownItem v-model="activeType" :options="TYPE_OPTIONS" />
    </DropdownMenu>
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <Empty v-if="!session.authenticated" :description="zhCN.session.missing" data-testid="mine-points-login">
        <Button type="primary" size="small" data-testid="mine-points-login-action" @click="requestLogin">
          {{ zhCN.login.submit }}
        </Button>
      </Empty>
      <div v-else-if="error && records.length === 0" class="points-error" role="alert" data-testid="mine-points-error">
        <p>{{ errorMessage }}</p>
        <Button type="primary" size="small" data-testid="mine-points-retry" @click="retry">
          {{ zhCN.common.retry }}
        </Button>
      </div>
      <Empty v-else-if="empty" :description="zhCN.empty.points" data-testid="mine-points-empty">
        <Button type="primary" size="small" data-testid="empty-go-home" @click="router.push('/home')">
          {{ zhCN.empty.goTasks }}
        </Button>
      </Empty>
      <List
        v-else
        v-model:loading="loading"
        v-model:error="error"
        :error-text="errorMessage"
        :finished="finished"
        :finished-text="zhCN.task.noMore"
        :immediate-check="false"
        data-testid="mine-points-list"
        @load="loadMore"
      >
        <button
          v-for="row in records"
          :key="rowKey(row)"
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
.mine-points {
  min-height: 100%;
  background: var(--portal-bg);
}
.points-error {
  padding: 24px 16px;
  text-align: center;
}
.points-balance [role="alert"] {
  display: flex;
  gap: 12px;
  align-items: center;
}
.mine-points :deep(.van-pull-refresh) {
  padding-top: 12px;
}
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
