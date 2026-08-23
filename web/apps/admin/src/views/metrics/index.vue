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
import { adminRowKey } from "@/utils/table";

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
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-select v-model:value="filters.grain" data-testid="filter-grain">
        <a-select-option value="DAY">{{ zhCN.metrics.day }}</a-select-option>
        <a-select-option value="WEEK">{{ zhCN.metrics.week }}</a-select-option>
        <a-select-option value="MONTH">{{ zhCN.metrics.month }}</a-select-option>
      </a-select>
      <a-input v-model:value="filters.dimKey" data-testid="filter-dim" :placeholder="zhCN.metrics.dimKey" />
      <a-date-picker v-model:value="filters.from" data-testid="filter-from" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
      <a-date-picker v-model:value="filters.to" data-testid="filter-to" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
      <a-button type="primary" data-testid="metrics-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-spin :spinning="loading">
    <div class="metrics-grid">
      <a-card data-testid="funnel-panel" :title="zhCN.metrics.funnel">
        <a-empty v-if="funnel.length === 0" :description="zhCN.common.empty" data-testid="funnel-empty" />
        <template v-else>
          <MetricsChart :option="funnelOption()" />
          <a-table size="small" :data-source="funnel" class="data-table admin-table" data-testid="funnel-table" :pagination="false" :row-key="adminRowKey">
      <a-table-column :title="zhCN.metrics.period">
        <template #default="{ record: row }">{{ row.period }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.dimKey">
        <template #default="{ record: row }">{{ row.dimKey }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.exposure">
        <template #default="{ record: row }">{{ row.exposureCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.start">
        <template #default="{ record: row }">{{ row.startCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.complete">
        <template #default="{ record: row }">{{ row.completeCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.startRate">
        <template #default="{ record: row }">{{ formatRate(row.startRate) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.completeRate">
        <template #default="{ record: row }">{{ formatRate(row.completeRate) }}</template>
      </a-table-column>
    </a-table>
        </template>
      </a-card>
      <a-card data-testid="spend-panel" :title="zhCN.metrics.spend">
        <a-empty v-if="spend.length === 0" :description="zhCN.common.empty" data-testid="spend-empty" />
        <template v-else>
          <MetricsChart :option="spendOption()" />
          <a-table size="small" :data-source="spend" class="data-table admin-table" data-testid="spend-table" :pagination="false" :row-key="adminRowKey">
      <a-table-column :title="zhCN.metrics.period">
        <template #default="{ record: row }">{{ row.period }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.dimKey">
        <template #default="{ record: row }">{{ row.dimKey }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.arrivedCount">
        <template #default="{ record: row }">{{ row.arrivedCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.arrivedCost">
        <template #default="{ record: row }">{{ row.arrivedCostFen }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.sendingCost">
        <template #default="{ record: row }">{{ row.sendingCostFen }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.stock">
        <template #default="{ record: row }">{{ row.remainingStock }}/{{ row.totalStock }}</template>
      </a-table-column>
    </a-table>
        </template>
      </a-card>
      <a-card data-testid="risk-panel" :title="zhCN.metrics.risk">
        <a-empty v-if="risk.length === 0" :description="zhCN.common.empty" data-testid="risk-empty" />
        <template v-else>
          <MetricsChart :option="riskOption()" />
          <a-table size="small" :data-source="risk" class="data-table admin-table" data-testid="risk-table" :pagination="false" :row-key="adminRowKey">
      <a-table-column :title="zhCN.metrics.period">
        <template #default="{ record: row }">{{ row.period }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.dimKey">
        <template #default="{ record: row }">{{ row.dimKey }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.hits">
        <template #default="{ record: row }">{{ row.hitCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.intercepts">
        <template #default="{ record: row }">{{ row.interceptCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.interceptRate">
        <template #default="{ record: row }">{{ formatRate(row.interceptRate) }}</template>
      </a-table-column>
    </a-table>
        </template>
      </a-card>
      <a-card data-testid="ad-panel" :title="zhCN.metrics.ad">
        <a-empty v-if="ads.length === 0" :description="zhCN.common.empty" data-testid="ad-empty" />
        <template v-else>
          <MetricsChart :option="adOption()" />
          <a-table size="small" :data-source="ads" class="data-table admin-table" data-testid="ad-table" :pagination="false" :row-key="adminRowKey">
      <a-table-column :title="zhCN.metrics.period">
        <template #default="{ record: row }">{{ row.period }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.dimKey">
        <template #default="{ record: row }">{{ row.dimKey }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.adExposure">
        <template #default="{ record: row }">{{ row.exposureCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.adClick">
        <template #default="{ record: row }">{{ row.clickCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metrics.ctr">
        <template #default="{ record: row }">{{ formatRate(row.ctr) }}</template>
      </a-table-column>
    </a-table>
        </template>
      </a-card>
    </div>
    </a-spin>
  </section>
</template>

<style scoped>
.metrics-grid {
  display: grid;
  gap: 16px;
}
</style>
