<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { pageSigninRecords, type SigninRecordView } from "@/api/signin";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "SigninRecordPage" });

const records = ref<SigninRecordView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ activityId: "", userId: "", from: "", to: "" });

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageSigninRecords({
    activityId: filters.activityId ? Number(filters.activityId) : undefined,
    userId: filters.userId ? Number(filters.userId) : undefined,
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
  <section class="admin-page" data-testid="signin-record-page">
    <h2>{{ zhCN.signin.recordTitle }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.activityId" data-testid="filter-activity" :placeholder="zhCN.signin.activityId" />
      <input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.signin.userId" />
      <input v-model="filters.from" type="date" data-testid="filter-from" />
      <input v-model="filters.to" type="date" data-testid="filter-to" />
      <button type="button" data-testid="signin-record-query" @click="load">{{ zhCN.common.query }}</button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="signin-record-table">
      <thead>
        <tr>
          <th>{{ zhCN.signin.activityId }}</th>
          <th>{{ zhCN.signin.userId }}</th>
          <th>{{ zhCN.signin.signDate }}</th>
          <th>{{ zhCN.signin.source }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.activityId }}</td>
          <td>{{ row.userId }}</td>
          <td>{{ row.signDate }}</td>
          <td>{{ row.source }}</td>
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
