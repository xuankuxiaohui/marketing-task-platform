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
    <div class="admin-page__header">
      <h2>{{ zhCN.metrics.title }}</h2>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-select v-model="filters.grain" data-testid="filter-grain">
        <el-option value="DAY" :label="zhCN.metrics.day" />
        <el-option value="WEEK" :label="zhCN.metrics.week" />
        <el-option value="MONTH" :label="zhCN.metrics.month" />
      </el-select>
      <el-input v-model="filters.dimKey" data-testid="filter-dim" :placeholder="zhCN.metrics.dimKey" />
      <el-input v-model="filters.from" type="date" data-testid="filter-from" />
      <el-input v-model="filters.to" type="date" data-testid="filter-to" />
      <el-button data-testid="metrics-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else class="metrics-grid">
      <article data-testid="funnel-panel">
        <h3>{{ zhCN.metrics.funnel }}</h3>
        <p v-if="funnel.length === 0" data-testid="funnel-empty">{{ zhCN.common.empty }}</p>
        <template v-else>
          <MetricsChart :option="funnelOption()" />
          <el-table :data="funnel" class="data-table admin-table" data-testid="funnel-table" size="small" stripe>
      <el-table-column :label="zhCN.metrics.period">
        <template #default="{ row }">{{ row.period }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.dimKey">
        <template #default="{ row }">{{ row.dimKey }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.exposure">
        <template #default="{ row }">{{ row.exposureCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.start">
        <template #default="{ row }">{{ row.startCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.complete">
        <template #default="{ row }">{{ row.completeCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.startRate">
        <template #default="{ row }">{{ formatRate(row.startRate) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.completeRate">
        <template #default="{ row }">{{ formatRate(row.completeRate) }}</template>
      </el-table-column>
    </el-table>
        </template>
      </article>
      <article data-testid="spend-panel">
        <h3>{{ zhCN.metrics.spend }}</h3>
        <p v-if="spend.length === 0" data-testid="spend-empty">{{ zhCN.common.empty }}</p>
        <template v-else>
          <MetricsChart :option="spendOption()" />
          <el-table :data="spend" class="data-table admin-table" data-testid="spend-table" size="small" stripe>
      <el-table-column :label="zhCN.metrics.period">
        <template #default="{ row }">{{ row.period }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.dimKey">
        <template #default="{ row }">{{ row.dimKey }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.arrivedCount">
        <template #default="{ row }">{{ row.arrivedCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.arrivedCost">
        <template #default="{ row }">{{ row.arrivedCostFen }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.sendingCost">
        <template #default="{ row }">{{ row.sendingCostFen }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.stock">
        <template #default="{ row }">{{ row.remainingStock }}/{{ row.totalStock }}</template>
      </el-table-column>
    </el-table>
        </template>
      </article>
      <article data-testid="risk-panel">
        <h3>{{ zhCN.metrics.risk }}</h3>
        <p v-if="risk.length === 0" data-testid="risk-empty">{{ zhCN.common.empty }}</p>
        <template v-else>
          <MetricsChart :option="riskOption()" />
          <el-table :data="risk" class="data-table admin-table" data-testid="risk-table" size="small" stripe>
      <el-table-column :label="zhCN.metrics.period">
        <template #default="{ row }">{{ row.period }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.dimKey">
        <template #default="{ row }">{{ row.dimKey }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.hits">
        <template #default="{ row }">{{ row.hitCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.intercepts">
        <template #default="{ row }">{{ row.interceptCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.interceptRate">
        <template #default="{ row }">{{ formatRate(row.interceptRate) }}</template>
      </el-table-column>
    </el-table>
        </template>
      </article>
      <article data-testid="ad-panel">
        <h3>{{ zhCN.metrics.ad }}</h3>
        <p v-if="ads.length === 0" data-testid="ad-empty">{{ zhCN.common.empty }}</p>
        <template v-else>
          <MetricsChart :option="adOption()" />
          <el-table :data="ads" class="data-table admin-table" data-testid="ad-table" size="small" stripe>
      <el-table-column :label="zhCN.metrics.period">
        <template #default="{ row }">{{ row.period }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.dimKey">
        <template #default="{ row }">{{ row.dimKey }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.adExposure">
        <template #default="{ row }">{{ row.exposureCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.adClick">
        <template #default="{ row }">{{ row.clickCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metrics.ctr">
        <template #default="{ row }">{{ formatRate(row.ctr) }}</template>
      </el-table-column>
    </el-table>
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
</style>
