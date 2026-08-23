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
    <a-spin :spinning="loading">
      <a-row :gutter="16" class="dashboard-cards" data-testid="dashboard-cards">
        <a-col :xs="12" :lg="6">
          <a-card data-testid="card-funnel">
            <a-statistic
              :title="zhCN.metrics.exposure"
              :value="exposure"
              :suffix="zhCN.dashboard.unitCount"
            />
          </a-card>
        </a-col>
        <a-col :xs="12" :lg="6">
          <a-card data-testid="card-spend">
            <a-statistic
              :title="zhCN.metrics.arrivedCost"
              :value="arrivedYuan"
              :suffix="`${zhCN.dashboard.unitYuan} · ${arrivedCostFen}${zhCN.dashboard.fenHint}`"
            />
          </a-card>
        </a-col>
        <a-col :xs="12" :lg="6">
          <a-card data-testid="card-risk">
            <a-statistic
              :title="zhCN.metrics.intercepts"
              :value="intercepts"
              :suffix="zhCN.dashboard.unitCount"
            />
          </a-card>
        </a-col>
        <a-col :xs="12" :lg="6">
          <a-card data-testid="card-ad">
            <a-statistic
              :title="zhCN.metrics.adClick"
              :value="adClicks"
              :suffix="zhCN.dashboard.unitCount"
            />
          </a-card>
        </a-col>
      </a-row>
      <a-card class="dashboard-inbox" :title="zhCN.dashboard.inbox" data-testid="dashboard-inbox">
        <a-empty v-if="inbox.length === 0" :description="zhCN.dashboard.inboxClear" />
        <ul v-else>
          <li v-for="item in inbox" :key="item.key">{{ item.label }} {{ item.value }}</li>
        </ul>
      </a-card>
    </a-spin>
  </section>
</template>

<style scoped>
.dashboard-welcome {
  margin: 0 0 16px;
  color: var(--admin-muted);
  font-size: 14px;
}
.dashboard-metrics-link {
  color: var(--admin-primary);
  font-size: 14px;
}
.dashboard-cards {
  margin-bottom: 16px;
}
.dashboard-inbox ul {
  margin: 0;
  padding: 0;
  list-style: none;
}
.dashboard-inbox li {
  padding: 4px 0;
}
</style>
