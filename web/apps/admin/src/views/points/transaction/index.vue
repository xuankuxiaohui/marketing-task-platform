<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { pagePointsTransactions, type PointsTransactionView } from "@/api/reward";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { POINT_TYPES } from "@/constants/reward";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "PointsTransactionPage" });

const records = ref<PointsTransactionView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ userId: "", type: "", from: "", to: "" });

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pagePointsTransactions({
    userId: filters.userId ? Number(filters.userId) : undefined,
    type: filters.type,
    from: filters.from || undefined,
    to: filters.to || undefined,
    page: page.value,
    pageSize,
  });
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  records.value = parsed.data?.records ?? [];
  total.value = parsed.data?.total ?? 0;
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="points-tx-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.points.txTitle }}</h2>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.points.userId" />
      <el-select v-model="filters.type" data-testid="filter-type">
        <el-option value="" :label="zhCN.points.type" />
        <el-option v-for="item in POINT_TYPES" :key="item" :value="item" :label="item" />
      </el-select>
      <el-input v-model="filters.from" type="datetime-local" />
      <el-input v-model="filters.to" type="datetime-local" />
      <el-button data-testid="tx-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="tx-table" size="small" stripe>
      <el-table-column :label="zhCN.points.userId">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.points.type">
        <template #default="{ row }">{{ row.type }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.points.amount">
        <template #default="{ row }">{{ row.amount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.points.balanceAfter">
        <template #default="{ row }">{{ row.balanceAfter }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.createdAt">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.prevPage }}</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.nextPage }}</el-button>
    </div>
  </section>
</template>
