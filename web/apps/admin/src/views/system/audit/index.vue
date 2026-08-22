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
    <div class="admin-page__header">
      <h2>{{ zhCN.audit.title }}</h2>
    </div>
    <p class="hint" data-testid="audit-no-delete">{{ zhCN.audit.noDelete }}</p>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.operatorId" data-testid="filter-operator" :placeholder="zhCN.audit.operatorId" />
      <el-input v-model="filters.module" data-testid="filter-module" :placeholder="zhCN.audit.module" />
      <el-input v-model="filters.action" data-testid="filter-action" :placeholder="zhCN.audit.action" />
      <el-input v-model="filters.result" data-testid="filter-result" :placeholder="zhCN.audit.result" />
      <el-input v-model="filters.from" data-testid="filter-from" type="datetime-local" />
      <el-input v-model="filters.to" data-testid="filter-to" type="datetime-local" />
      <el-button data-testid="audit-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="audit-table" size="small" stripe>
      <el-table-column :label="zhCN.audit.module">
        <template #default="{ row }">{{ row.module }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.audit.action">
        <template #default="{ row }">{{ row.action }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.audit.operatorName">
        <template #default="{ row }">{{ row.operatorName }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.audit.result">
        <template #default="{ row }">{{ row.result }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.audit.summary">
        <template #default="{ row }">{{ row.requestSummary }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.audit.costMs">
        <template #default="{ row }">{{ row.costMs }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.audit.traceId">
        <template #default="{ row }">{{ row.traceId }}</template>
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
