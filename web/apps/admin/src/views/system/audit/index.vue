<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { pageAudits, type AuditView } from "@/api/system";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "AuditLogPage" });

const records = ref<AuditView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({
  operatorId: "",
  module: "",
  action: "",
  result: "",
  from: "",
  to: "",
});

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const toIso = (value: string): string | undefined => {
    if (!value) {
      return undefined;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toISOString();
  };
  const result = await pageAudits({
    operatorId: filters.operatorId ? Number(filters.operatorId) : undefined,
    module: filters.module,
    action: filters.action,
    result: filters.result,
    from: toIso(filters.from),
    to: toIso(filters.to),
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
  <section class="admin-page" data-testid="audit-page">
    <h2>{{ zhCN.audit.title }}</h2>
    <p class="hint" data-testid="audit-no-delete">{{ zhCN.audit.noDelete }}</p>
    <div class="admin-toolbar">
      <input v-model="filters.operatorId" data-testid="filter-operator" :placeholder="zhCN.audit.operatorId" />
      <input v-model="filters.module" data-testid="filter-module" :placeholder="zhCN.audit.module" />
      <input v-model="filters.action" data-testid="filter-action" :placeholder="zhCN.audit.action" />
      <input v-model="filters.result" data-testid="filter-result" :placeholder="zhCN.audit.result" />
      <input v-model="filters.from" data-testid="filter-from" type="datetime-local" />
      <input v-model="filters.to" data-testid="filter-to" type="datetime-local" />
      <button type="button" data-testid="audit-query" @click="load">{{ zhCN.common.query }}</button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="audit-table">
      <thead>
        <tr>
          <th>{{ zhCN.audit.module }}</th>
          <th>{{ zhCN.audit.action }}</th>
          <th>{{ zhCN.audit.operatorName }}</th>
          <th>{{ zhCN.audit.result }}</th>
          <th>{{ zhCN.audit.summary }}</th>
          <th>{{ zhCN.audit.costMs }}</th>
          <th>{{ zhCN.audit.traceId }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.module }}</td>
          <td>{{ row.action }}</td>
          <td>{{ row.operatorName }}</td>
          <td>{{ row.result }}</td>
          <td>{{ row.requestSummary }}</td>
          <td>{{ row.costMs }}</td>
          <td>{{ row.traceId }}</td>
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
