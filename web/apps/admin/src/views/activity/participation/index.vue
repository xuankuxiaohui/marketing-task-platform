<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { activityStats, pageParticipations, type ParticipationStatsView, type ParticipationView } from "@/api/activity";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "ActivityParticipationPage" });

const records = ref<ParticipationView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="activity-participation-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.activity.participationTitle }}</h2>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.activityId" data-testid="filter-activity" :placeholder="zhCN.activity.activityId" />
      <el-input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.activity.userId" />
      <el-select v-model="filters.result" data-testid="filter-result">
        <el-option value="" :label="zhCN.activity.result" />
        <el-option value="PASS" label="PASS" />
        <el-option value="REJECT" label="REJECT" />
      </el-select>
      <el-button data-testid="activity-participation-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <p v-if="stats" data-testid="activity-stats">
      {{ zhCN.activity.stats }} {{ stats.total }} / {{ zhCN.activity.passRate }}
      {{ Math.round(stats.passRate * 100) }}%
    </p>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="activity-participation-table" size="small" stripe>
      <el-table-column :label="zhCN.activity.activityId">
        <template #default="{ row }">{{ row.activityId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.activity.userId">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.activity.periodKey">
        <template #default="{ row }">{{ row.periodKey }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.activity.result">
        <template #default="{ row }">{{ row.result }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.activity.hitRule">
        <template #default="{ row }">{{ row.hitRule }}</template>
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
