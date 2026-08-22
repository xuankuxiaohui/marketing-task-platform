<script setup lang="ts">
import { onMounted, ref } from "vue";
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
const arrivedCost = ref(0);
const intercepts = ref(0);
const adClicks = ref(0);

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
  arrivedCost.value = (spendParsed.data?.records ?? []).reduce((sum, row) => sum + row.arrivedCostFen, 0);
  intercepts.value = (riskParsed.data?.records ?? []).reduce((sum, row) => sum + row.interceptCount, 0);
  adClicks.value = (adParsed.data?.records ?? []).reduce((sum, row) => sum + row.clickCount, 0);
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page dashboard-page" data-testid="dashboard-page">
    <h2>{{ zhCN.dashboard.title }}</h2>
    <p>{{ zhCN.dashboard.welcome }}{{ session.nickname ? `，${session.nickname}` : "" }}</p>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <ul v-else class="dashboard-cards" data-testid="dashboard-cards">
      <li data-testid="card-funnel">{{ zhCN.metrics.exposure }} {{ exposure }}</li>
      <li data-testid="card-spend">{{ zhCN.metrics.arrivedCost }} {{ arrivedCost }}</li>
      <li data-testid="card-risk">{{ zhCN.metrics.intercepts }} {{ intercepts }}</li>
      <li data-testid="card-ad">{{ zhCN.metrics.adClick }} {{ adClicks }}</li>
    </ul>
    <p>
      <RouterLink data-testid="metrics-link" to="/metrics">{{ zhCN.metrics.open }}</RouterLink>
    </p>
  </section>
</template>

<style scoped>
.dashboard-page {
  /* page chrome comes from .admin-page */
}
.dashboard-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 0;
  list-style: none;
}
.dashboard-cards li {
  border: 1px solid #e2e8f0;
  padding: 12px;
  background: #fff;
}
</style>
