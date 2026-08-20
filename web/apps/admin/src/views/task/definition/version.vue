<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { diffVersions, listVersions, type TaskVersionView, type VersionDiffResponse } from "@/api/task";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

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
    <h2>{{ zhCN.task.versionTitle }}</h2>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="versions.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="version-table">
      <thead>
        <tr>
          <th>{{ zhCN.task.version }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in versions" :key="row.version">
          <td>{{ row.version }}</td>
          <td>{{ formatDateTime(row.publishedAt) }}</td>
        </tr>
      </tbody>
    </table>
    <div class="admin-toolbar">
      <input v-model="left" data-testid="diff-left" :placeholder="zhCN.task.version" />
      <input v-model="right" data-testid="diff-right" :placeholder="zhCN.task.version" />
      <button type="button" data-testid="diff-run" @click="loadDiff">对比</button>
    </div>
    <pre v-if="diff" data-testid="diff-result">{{ JSON.stringify(diff, null, 2) }}</pre>
  </section>
</template>
