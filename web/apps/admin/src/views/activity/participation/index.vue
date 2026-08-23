<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { activityStats, pageParticipations, type ParticipationStatsView, type ParticipationView } from "@/api/activity";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "ActivityParticipationPage" });

const records = ref<ParticipationView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const stats = ref<ParticipationStatsView | null>(null);
const filters = reactive({ activityId: "", userId: "", result: "" });

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const activityId = filters.activityId ? Number(filters.activityId) : undefined;
  const result = await pageParticipations({
    activityId,
    userId: filters.userId ? Number(filters.userId) : undefined,
    result: filters.result || undefined,
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
  if (activityId) {
    const statsResult = await activityStats(activityId);
    const statsParsed = okOrFeedback(statsResult);
    stats.value = statsParsed.ok ? (statsParsed.data ?? null) : null;
  } else {
    stats.value = null;
  }
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
  <section class="admin-page" data-testid="activity-participation-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.activity.participationTitle }}</h2>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.activityId" data-testid="filter-activity" :placeholder="zhCN.activity.activityId" />
      <a-input v-model:value="filters.userId" data-testid="filter-user" :placeholder="zhCN.activity.userId" />
      <a-select v-model:value="filters.result" data-testid="filter-result">
        <a-select-option value="">{{ zhCN.activity.result }}</a-select-option>
        <a-select-option value="PASS">PASS</a-select-option>
        <a-select-option value="REJECT">REJECT</a-select-option>
      </a-select>
      <a-button type="primary" data-testid="activity-participation-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <p v-if="stats" data-testid="activity-stats">
      {{ zhCN.activity.stats }} {{ stats.total }} / {{ zhCN.activity.passRate }}
      {{ Math.round(stats.passRate * 100) }}%
    </p>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="activity-participation-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.activity.activityId">
        <template #default="{ record: row }">{{ row.activityId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.activity.userId">
        <template #default="{ record: row }">{{ row.userId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.activity.periodKey">
        <template #default="{ record: row }">{{ row.periodKey }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.activity.result">
        <template #default="{ record: row }">{{ row.result }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.activity.hitRule">
        <template #default="{ record: row }">{{ row.hitRule }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
    </a-table>
  </section>
</template>
