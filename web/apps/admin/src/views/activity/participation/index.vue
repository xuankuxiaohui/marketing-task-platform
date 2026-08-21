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
    <h2>{{ zhCN.activity.participationTitle }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.activityId" data-testid="filter-activity" :placeholder="zhCN.activity.activityId" />
      <input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.activity.userId" />
      <select v-model="filters.result" data-testid="filter-result">
        <option value="">{{ zhCN.activity.result }}</option>
        <option value="PASS">PASS</option>
        <option value="REJECT">REJECT</option>
      </select>
      <button type="button" data-testid="activity-participation-query" @click="load">{{ zhCN.common.query }}</button>
    </div>
    <p v-if="stats" data-testid="activity-stats">
      {{ zhCN.activity.stats }} {{ stats.total }} / {{ zhCN.activity.passRate }}
      {{ Math.round(stats.passRate * 100) }}%
    </p>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="activity-participation-table">
      <thead>
        <tr>
          <th>{{ zhCN.activity.activityId }}</th>
          <th>{{ zhCN.activity.userId }}</th>
          <th>{{ zhCN.activity.periodKey }}</th>
          <th>{{ zhCN.activity.result }}</th>
          <th>{{ zhCN.activity.hitRule }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.activityId }}</td>
          <td>{{ row.userId }}</td>
          <td>{{ row.periodKey }}</td>
          <td>{{ row.result }}</td>
          <td>{{ row.hitRule }}</td>
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
