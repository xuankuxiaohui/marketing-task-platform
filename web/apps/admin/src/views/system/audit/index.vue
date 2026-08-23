<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { pageAudits, type AuditView } from "@/api/system";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import EllipsisCell from "@/components/EllipsisCell.vue";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "AuditLogPage" });

const records = ref<AuditView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
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
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.operatorId" data-testid="filter-operator" :placeholder="zhCN.audit.operatorId" />
      <a-input v-model:value="filters.module" data-testid="filter-module" :placeholder="zhCN.audit.module" />
      <a-input v-model:value="filters.action" data-testid="filter-action" :placeholder="zhCN.audit.action" />
      <a-input v-model:value="filters.result" data-testid="filter-result" :placeholder="zhCN.audit.result" />
      <a-date-picker v-model:value="filters.from" data-testid="filter-from" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-date-picker v-model:value="filters.to" data-testid="filter-to" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-button type="primary" data-testid="audit-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table audit-table" data-testid="audit-table" table-layout="fixed" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.audit.module" :width="120">
        <template #default="{ record: row }"><EllipsisCell :value="row.module" :max="16" /></template>
      </a-table-column>
      <a-table-column :title="zhCN.audit.action" :width="160">
        <template #default="{ record: row }"><EllipsisCell :value="row.action" :max="20" /></template>
      </a-table-column>
      <a-table-column :title="zhCN.audit.operatorName" :width="120">
        <template #default="{ record: row }"><EllipsisCell :value="row.operatorName" :max="16" /></template>
      </a-table-column>
      <a-table-column :title="zhCN.audit.result" :width="100">
        <template #default="{ record: row }"><EllipsisCell :value="row.result" :max="12" /></template>
      </a-table-column>
      <a-table-column :title="zhCN.audit.summary">
        <template #default="{ record: row }">
          <span data-testid="audit-summary"><EllipsisCell :value="row.requestSummary" :max="28" /></span>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.audit.costMs" :width="88">
        <template #default="{ record: row }">{{ row.costMs }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.audit.traceId" :width="140">
        <template #default="{ record: row }">
          <span data-testid="audit-trace"><EllipsisCell :value="row.traceId" :max="16" /></span>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
    </a-table>
  </section>
</template>

<style scoped>
.audit-table :deep(.ant-table-cell) {
  overflow: hidden;
}
</style>
