<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  fetchAdMetrics,
  fetchFunnel,
  fetchRiskMetrics,
  fetchSpendMetrics,
  type AdPointView,
  type FunnelPointView,
  type MetricsGrain,
  type RiskPointView,
  type SpendPointView,
} from "@/api/metrics";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import MetricsChart from "./MetricsChart.vue";

defineOptions({ name: "MetricsDashboardPage" });

const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const funnel = ref<FunnelPointView[]>([]);
const spend = ref<SpendPointView[]>([]);
const risk = ref<RiskPointView[]>([]);
const ads = ref<AdPointView[]>([]);
const filters = reactive({ grain: "DAY" as MetricsGrain, dimKey: "", from: "", to: "" });

function shanghaiBound(day: string, end: boolean): string | undefined {
  if (!day) {
    return undefined;
  }
  return end ? `${day}T23:59:59+08:00` : `${day}T00:00:00+08:00`;
}

function formatRate(value: number | null | undefined): string {
  if (value == null) {
    return "—";
  }
  return `${(value * 100).toFixed(1)}%`;
}

function query() {
  return {
    grain: filters.grain,
    dimKey: filters.dimKey || undefined,
    from: shanghaiBound(filters.from, false),
    to: shanghaiBound(filters.to, true),
  };
}

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const params = query();
  const [funnelResult, spendResult, riskResult, adResult] = await Promise.all([
    fetchFunnel(params),
    fetchSpendMetrics(params),
    fetchRiskMetrics(params),
    fetchAdMetrics(params),
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
  funnel.value = funnelParsed.data?.records ?? [];
  spend.value = spendParsed.data?.records ?? [];
  risk.value = riskParsed.data?.records ?? [];
  ads.value = adParsed.data?.records ?? [];
}

function funnelOption(): Record<string, unknown> {
  const latest = funnel.value[0];
  return {
    tooltip: { trigger: "item" },
    series: [
      {
        type: "funnel",
        data: [
          { name: zhCN.metrics.exposure, value: latest?.exposureCount ?? 0 },
          { name: zhCN.metrics.start, value: latest?.startCount ?? 0 },
          { name: zhCN.metrics.complete, value: latest?.completeCount ?? 0 },
        ],
      },
    ],
  };
}

function spendOption(): Record<string, unknown> {
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: spend.value.map((row) => row.period) },
    yAxis: { type: "value" },
    series: [
      { type: "bar", name: zhCN.metrics.arrivedCost, data: spend.value.map((row) => row.arrivedCostFen) },
      { type: "bar", name: zhCN.metrics.sendingCost, data: spend.value.map((row) => row.sendingCostFen) },
    ],
  };
}

function riskOption(): Record<string, unknown> {
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: risk.value.map((row) => row.dimKey) },
    yAxis: { type: "value" },
    series: [{ type: "bar", name: zhCN.metrics.hits, data: risk.value.map((row) => row.hitCount) }],
  };
}

function adOption(): Record<string, unknown> {
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: ads.value.map((row) => row.period) },
    yAxis: { type: "value" },
    series: [
      { type: "line", name: zhCN.metrics.adExposure, data: ads.value.map((row) => row.exposureCount) },
      { type: "line", name: zhCN.metrics.adClick, data: ads.value.map((row) => row.clickCount) },
    ],
  };
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="metrics-page">
    <h2>{{ zhCN.metrics.title }}</h2>
    <div class="admin-toolbar">
      <select v-model="filters.grain" data-testid="filter-grain">
        <option value="DAY">{{ zhCN.metrics.day }}</option>
        <option value="WEEK">{{ zhCN.metrics.week }}</option>
        <option value="MONTH">{{ zhCN.metrics.month }}</option>
      </select>
      <input v-model="filters.dimKey" data-testid="filter-dim" :placeholder="zhCN.metrics.dimKey" />
      <input v-model="filters.from" type="date" data-testid="filter-from" />
      <input v-model="filters.to" type="date" data-testid="filter-to" />
      <button type="button" data-testid="metrics-query" @click="load">{{ zhCN.common.query }}</button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else class="metrics-grid">
      <article data-testid="funnel-panel">
        <h3>{{ zhCN.metrics.funnel }}</h3>
        <p v-if="funnel.length === 0" data-testid="funnel-empty">{{ zhCN.common.empty }}</p>
        <template v-else>
          <MetricsChart :option="funnelOption()" />
          <table class="data-table" data-testid="funnel-table">
            <thead>
              <tr>
                <th>{{ zhCN.metrics.period }}</th>
                <th>{{ zhCN.metrics.dimKey }}</th>
                <th>{{ zhCN.metrics.exposure }}</th>
                <th>{{ zhCN.metrics.start }}</th>
                <th>{{ zhCN.metrics.complete }}</th>
                <th>{{ zhCN.metrics.startRate }}</th>
                <th>{{ zhCN.metrics.completeRate }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in funnel" :key="`${row.period}-${row.dimKey}`">
                <td>{{ row.period }}</td>
                <td>{{ row.dimKey }}</td>
                <td>{{ row.exposureCount }}</td>
                <td>{{ row.startCount }}</td>
                <td>{{ row.completeCount }}</td>
                <td>{{ formatRate(row.startRate) }}</td>
                <td>{{ formatRate(row.completeRate) }}</td>
              </tr>
            </tbody>
          </table>
        </template>
      </article>
      <article data-testid="spend-panel">
        <h3>{{ zhCN.metrics.spend }}</h3>
        <p v-if="spend.length === 0" data-testid="spend-empty">{{ zhCN.common.empty }}</p>
        <template v-else>
          <MetricsChart :option="spendOption()" />
          <table class="data-table" data-testid="spend-table">
            <thead>
              <tr>
                <th>{{ zhCN.metrics.period }}</th>
                <th>{{ zhCN.metrics.dimKey }}</th>
                <th>{{ zhCN.metrics.arrivedCount }}</th>
                <th>{{ zhCN.metrics.arrivedCost }}</th>
                <th>{{ zhCN.metrics.sendingCost }}</th>
                <th>{{ zhCN.metrics.stock }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in spend" :key="`${row.period}-${row.dimKey}`">
                <td>{{ row.period }}</td>
                <td>{{ row.dimKey }}</td>
                <td>{{ row.arrivedCount }}</td>
                <td>{{ row.arrivedCostFen }}</td>
                <td>{{ row.sendingCostFen }}</td>
                <td>{{ row.remainingStock }}/{{ row.totalStock }}</td>
              </tr>
            </tbody>
          </table>
        </template>
      </article>
      <article data-testid="risk-panel">
        <h3>{{ zhCN.metrics.risk }}</h3>
        <p v-if="risk.length === 0" data-testid="risk-empty">{{ zhCN.common.empty }}</p>
        <template v-else>
          <MetricsChart :option="riskOption()" />
          <table class="data-table" data-testid="risk-table">
            <thead>
              <tr>
                <th>{{ zhCN.metrics.period }}</th>
                <th>{{ zhCN.metrics.dimKey }}</th>
                <th>{{ zhCN.metrics.hits }}</th>
                <th>{{ zhCN.metrics.intercepts }}</th>
                <th>{{ zhCN.metrics.interceptRate }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in risk" :key="`${row.period}-${row.dimKey}`">
                <td>{{ row.period }}</td>
                <td>{{ row.dimKey }}</td>
                <td>{{ row.hitCount }}</td>
                <td>{{ row.interceptCount }}</td>
                <td>{{ formatRate(row.interceptRate) }}</td>
              </tr>
            </tbody>
          </table>
        </template>
      </article>
      <article data-testid="ad-panel">
        <h3>{{ zhCN.metrics.ad }}</h3>
        <p v-if="ads.length === 0" data-testid="ad-empty">{{ zhCN.common.empty }}</p>
        <template v-else>
          <MetricsChart :option="adOption()" />
          <table class="data-table" data-testid="ad-table">
            <thead>
              <tr>
                <th>{{ zhCN.metrics.period }}</th>
                <th>{{ zhCN.metrics.dimKey }}</th>
                <th>{{ zhCN.metrics.adExposure }}</th>
                <th>{{ zhCN.metrics.adClick }}</th>
                <th>{{ zhCN.metrics.ctr }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in ads" :key="`${row.period}-${row.dimKey}`">
                <td>{{ row.period }}</td>
                <td>{{ row.dimKey }}</td>
                <td>{{ row.exposureCount }}</td>
                <td>{{ row.clickCount }}</td>
                <td>{{ formatRate(row.ctr) }}</td>
              </tr>
            </tbody>
          </table>
        </template>
      </article>
    </div>
  </section>
</template>

<style scoped>
.metrics-grid {
  display: grid;
  gap: 16px;
}
.metrics-grid article {
  background: #fff;
  padding: 12px;
  border: 1px solid #e2e8f0;
}
</style>
