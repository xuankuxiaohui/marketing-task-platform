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
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.activityId" data-testid="filter-activity" :placeholder="zhCN.signin.activityId" />
      <el-input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.signin.userId" />
      <el-input v-model="filters.from" type="date" data-testid="filter-from" />
      <el-input v-model="filters.to" type="date" data-testid="filter-to" />
      <el-button data-testid="signin-record-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="records" class="data-table" data-testid="signin-record-table" stripe>
      <el-table-column :label="zhCN.signin.activityId">
        <template #default="{ row }">{{ row.activityId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.signin.userId">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.signin.signDate">
        <template #default="{ row }">{{ row.signDate }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.signin.source">
        <template #default="{ row }">{{ row.source }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.createdAt">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</el-button>
    </div>
  </section>
</template>
