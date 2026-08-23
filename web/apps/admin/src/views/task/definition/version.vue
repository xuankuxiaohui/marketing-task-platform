<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { diffVersions, listVersions, type TaskVersionView, type VersionDiffResponse } from "@/api/task";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { adminRowKey } from "@/utils/table";

defineOptions({ name: "TaskDefinitionVersionPage" });

const route = useRoute();
const versions = ref<TaskVersionView[]>([]);
const diff = ref<VersionDiffResponse | null>(null);
const feedback = ref<PageFeedback | null>(null);
const left = ref("");
const right = ref("");

function taskId(): number | undefined {
  const raw = route.params.id;
  const value = Array.isArray(raw) ? raw[0] : raw;
  const id = Number(value);
  return Number.isFinite(id) ? id : undefined;
}

async function load(): Promise<void> {
  const id = taskId();
  if (id == null) {
    return;
  }
  feedback.value = null;
  const result = await listVersions(id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  versions.value = parsed.data ?? [];
}

async function loadDiff(): Promise<void> {
  const id = taskId();
  if (id == null || !left.value || !right.value) {
    return;
  }
  const result = await diffVersions(id, Number(left.value), Number(right.value));
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  diff.value = parsed.data ?? null;
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="task-version-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.task.versionTitle }}</h2>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :data-source="versions" class="data-table" data-testid="version-table" :pagination="false" :row-key="adminRowKey">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.task.version">
        <template #default="{ record: row }">{{ row.version }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.publishedAt) }}</template>
      </a-table-column>
    </a-table>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="left" data-testid="diff-left" :placeholder="zhCN.task.version" />
      <a-input v-model:value="right" data-testid="diff-right" :placeholder="zhCN.task.version" />
      <a-button data-testid="diff-run" @click="loadDiff">对比</a-button>
    </a-form>
    <pre v-if="diff" data-testid="diff-result">{{ JSON.stringify(diff, null, 2) }}</pre>
  </section>
</template>
