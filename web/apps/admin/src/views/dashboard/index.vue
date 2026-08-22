<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { fetchAdMetrics, fetchFunnel, fetchRiskMetrics, fetchSpendMetrics } from "@/api/metrics";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "DashboardPage" });

const session = useSessionStore();
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const exposure = ref(0);
const arrivedCostFen = ref(0);
const remainingStock = ref(0);
const intercepts = ref(0);
const adClicks = ref(0);

const arrivedYuan = computed(() => (arrivedCostFen.value / 100).toFixed(2));
const inbox = computed(() => {
  const items: { key: string; label: string; value: number }[] = [];
  if (intercepts.value > 0) {
    items.push({ key: "risk", label: zhCN.dashboard.interceptToday, value: intercepts.value });
  }
  if (remainingStock.value <= 0) {
    items.push({ key: "stock", label: zhCN.dashboard.stockAlert, value: remainingStock.value });
  }
  return items;
});

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const [funnelResult, spendResult, riskResult, adResult] = await Promise.all([
    fetchFunnel({ grain: "DAY" }),
    fetchSpendMetrics({ grain: "DAY" }),
    fetchRiskMetrics({ grain: "DAY" }),
    fetchAdMetrics({ grain: "DAY" }),
  ]);
  loading.value = false;
  const funnelParsed = okOrFeedback(funnelResult);
  const spendParsed = okOrFeedback(spendResult);
  const riskParsed = okOrFeedback(riskResult);
  const adParsed = okOrFeedback(adResult);
  if (!funnelParsed.ok) {
    feedback.value = funnelParsed.feedback;
    return;
  }
  if (!spendParsed.ok) {
    feedback.value = spendParsed.feedback;
    return;
  }
  if (!riskParsed.ok) {
    feedback.value = riskParsed.feedback;
    return;
  }
  if (!adParsed.ok) {
    feedback.value = adParsed.feedback;
    return;
  }
  exposure.value = (funnelParsed.data?.records ?? []).reduce((sum, row) => sum + row.exposureCount, 0);
  arrivedCostFen.value = (spendParsed.data?.records ?? []).reduce((sum, row) => sum + row.arrivedCostFen, 0);
  remainingStock.value = (spendParsed.data?.records ?? []).reduce((sum, row) => sum + row.remainingStock, 0);
  intercepts.value = (riskParsed.data?.records ?? []).reduce((sum, row) => sum + row.interceptCount, 0);
  adClicks.value = (adParsed.data?.records ?? []).reduce((sum, row) => sum + row.clickCount, 0);
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page admin-page--flush dashboard-page" data-testid="dashboard-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.dashboard.title }}</h2>
      <RouterLink data-testid="metrics-link" class="dashboard-metrics-link" to="/metrics">
        {{ zhCN.metrics.open }}
      </RouterLink>
    </div>
    <p class="dashboard-welcome">
      {{ zhCN.dashboard.welcome }}{{ session.nickname ? ` · ${session.nickname}` : "" }}
    </p>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <ul v-else class="dashboard-cards" data-testid="dashboard-cards">
      <li data-testid="card-funnel">
        <span>{{ zhCN.metrics.exposure }}</span>
        <strong>{{ exposure }}</strong>
        <em>{{ zhCN.dashboard.unitCount }}</em>
      </li>
      <li data-testid="card-spend">
        <span>{{ zhCN.metrics.arrivedCost }}</span>
        <strong>{{ arrivedYuan }}</strong>
        <em>{{ zhCN.dashboard.unitYuan }} · {{ arrivedCostFen }}{{ zhCN.dashboard.fenHint }}</em>
      </li>
      <li data-testid="card-risk">
        <span>{{ zhCN.metrics.intercepts }}</span>
        <strong>{{ intercepts }}</strong>
        <em>{{ zhCN.dashboard.unitCount }}</em>
      </li>
      <li data-testid="card-ad">
        <span>{{ zhCN.metrics.adClick }}</span>
        <strong>{{ adClicks }}</strong>
        <em>{{ zhCN.dashboard.unitCount }}</em>
      </li>
    </ul>
    <div class="dashboard-inbox" data-testid="dashboard-inbox">
      <h3>{{ zhCN.dashboard.inbox }}</h3>
      <p v-if="inbox.length === 0">{{ zhCN.dashboard.inboxClear }}</p>
      <ul v-else>
        <li v-for="item in inbox" :key="item.key">{{ item.label }} {{ item.value }}</li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.dashboard-welcome {
  margin: 0;
  color: var(--admin-muted);
  font-size: 13px;
}
.dashboard-metrics-link {
  color: var(--el-color-primary);
  font-size: 13px;
  text-decoration: none;
}
.dashboard-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 0;
  list-style: none;
}
.dashboard-cards li {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-card-radius);
  padding: 14px 16px;
  background: var(--admin-surface);
}
.dashboard-cards span {
  color: var(--admin-muted);
  font-size: 12px;
}
.dashboard-cards strong {
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
}
.dashboard-cards em {
  font-style: normal;
  color: var(--admin-muted);
  font-size: 12px;
}
.dashboard-inbox {
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-card-radius);
  padding: 14px 16px;
  background: var(--admin-surface);
}
.dashboard-inbox h3 {
  margin: 0 0 8px;
  font-size: 14px;
}
.dashboard-inbox p,
.dashboard-inbox ul {
  margin: 0;
  padding: 0;
  color: var(--admin-muted);
  font-size: 13px;
  list-style: none;
}
@media (max-width: 960px) {
  .dashboard-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
