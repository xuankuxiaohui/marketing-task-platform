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
    <h2>{{ zhCN.points.txTitle }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.points.userId" />
      <select v-model="filters.type" data-testid="filter-type">
        <option value="">{{ zhCN.points.type }}</option>
        <option v-for="item in POINT_TYPES" :key="item" :value="item">{{ item }}</option>
      </select>
      <input v-model="filters.from" type="datetime-local" />
      <input v-model="filters.to" type="datetime-local" />
      <button type="button" data-testid="tx-query" @click="load">{{ zhCN.common.query }}</button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="tx-table">
      <thead>
        <tr>
          <th>{{ zhCN.points.userId }}</th>
          <th>{{ zhCN.points.type }}</th>
          <th>{{ zhCN.points.amount }}</th>
          <th>{{ zhCN.points.balanceAfter }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.userId }}</td>
          <td>{{ row.type }}</td>
          <td>{{ row.amount }}</td>
          <td>{{ row.balanceAfter }}</td>
          <td>{{ formatDateTime(row.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <button type="button" :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</button>
      <span>{{ page }}</span>
      <button type="button" :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</button>
    </div>
  </section>
</template>
