<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { pagePointsTransactions, type PointsTransactionView } from "@/api/reward";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { POINT_TYPES } from "@/constants/reward";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "PointsTransactionPage" });

const records = ref<PointsTransactionView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
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
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.userId" data-testid="filter-user" :placeholder="zhCN.points.userId" />
      <a-select v-model:value="filters.type" data-testid="filter-type">
        <a-select-option value="">{{ zhCN.points.type }}</a-select-option>
        <a-select-option v-for="item in POINT_TYPES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      <a-date-picker v-model:value="filters.from" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-date-picker v-model:value="filters.to" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-button type="primary" data-testid="tx-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="tx-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.points.userId">
        <template #default="{ record: row }">{{ row.userId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.points.type">
        <template #default="{ record: row }">{{ row.type }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.points.amount">
        <template #default="{ record: row }">{{ row.amount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.points.balanceAfter">
        <template #default="{ record: row }">{{ row.balanceAfter }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
    </a-table>
  </section>
</template>
