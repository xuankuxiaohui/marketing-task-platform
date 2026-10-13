<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { pageSigninRecords, type SigninRecordView } from "@/api/signin";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import ListLoadFailure from "@/components/ListLoadFailure.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";
import { useLatestRequest } from "@/composables/useLatestRequest";
import { applyListQueryOutcome, runListQuery } from "@/composables/runListQuery";

defineOptions({ name: "SigninRecordPage" });

const records = ref<SigninRecordView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const listFailed = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ activityId: "", userId: "", from: "", to: "" });

const beginLoad = useLatestRequest(() => [
  page.value,
  filters.activityId,
  filters.userId,
  filters.from,
  filters.to,
]);

async function load(): Promise<void> {
  const isCurrent = beginLoad();
  const outcome = await runListQuery(loading, isCurrent, async () => {
    feedback.value = null;
    const result = await pageSigninRecords({
      activityId: filters.activityId ? Number(filters.activityId) : undefined,
      userId: filters.userId ? Number(filters.userId) : undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      page: page.value,
      pageSize,
    });
    if (!isCurrent()) {
      return true;
    }
    const parsed = okOrFeedback(result);
    if (!parsed.ok) {
      return false;
    }
    records.value = parsed.data?.records ?? [];
    total.value = parsed.data?.total ?? 0;
    return true;
  });
  applyListQueryOutcome(outcome, listFailed, () => {
    records.value = [];
    total.value = 0;
  });
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
  <section class="admin-page" data-testid="signin-record-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.signin.recordTitle }}</h2>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.activityId" data-testid="filter-activity" :placeholder="zhCN.signin.activityId" />
      <a-input v-model:value="filters.userId" data-testid="filter-user" :placeholder="zhCN.signin.userId" />
      <a-date-picker v-model:value="filters.from" data-testid="filter-from" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
      <a-date-picker v-model:value="filters.to" data-testid="filter-to" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
      <a-button type="primary" data-testid="signin-record-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="signin-record-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <ListLoadFailure v-if="listFailed" :loading="loading" @reload="load" />
      <a-empty v-else :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.signin.activityId">
        <template #default="{ record: row }">{{ row.activityId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.signin.userId">
        <template #default="{ record: row }">{{ row.userId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.signin.signDate">
        <template #default="{ record: row }">{{ row.signDate }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.signin.source">
        <template #default="{ record: row }">{{ row.source }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
    </a-table>
  </section>
</template>
