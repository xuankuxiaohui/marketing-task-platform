<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  abandonInstance,
  getInstance,
  pageInstances,
  type AdminInstanceDetailResponse,
  type AdminInstanceView,
} from "@/api/task";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { INSTANCE_STATUS } from "@/constants/task";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "TaskInstancePage" });

const records = ref<AdminInstanceView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ taskId: "", userId: "", status: "", simulated: "" });
const detail = ref<AdminInstanceDetailResponse | null>(null);
const abandonOpen = ref(false);
const abandoning = ref<AdminInstanceView | null>(null);
const reason = ref("");

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageInstances({
    taskId: filters.taskId ? Number(filters.taskId) : undefined,
    userId: filters.userId ? Number(filters.userId) : undefined,
    status: filters.status,
    simulated: filters.simulated === "" ? undefined : Number(filters.simulated),
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

async function openDetail(row: AdminInstanceView): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await getInstance(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  detail.value = parsed.data ?? null;
}

function openAbandon(row: AdminInstanceView): void {
  abandoning.value = row;
  reason.value = "";
  abandonOpen.value = true;
}

async function submitAbandon(): Promise<void> {
  if (abandoning.value?.id == null) {
    return;
  }
  const result = await abandonInstance(abandoning.value.id, { reason: reason.value });
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  abandonOpen.value = false;
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="instance-page">
    <h2>{{ zhCN.instance.title }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.taskId" data-testid="filter-task-id" :placeholder="zhCN.instance.taskId" />
      <input v-model="filters.userId" data-testid="filter-user-id" :placeholder="zhCN.instance.userId" />
      <select v-model="filters.status" data-testid="filter-status">
        <option value="">{{ zhCN.common.status }}</option>
        <option v-for="item in Object.values(INSTANCE_STATUS)" :key="item" :value="item">{{ item }}</option>
      </select>
      <select v-model="filters.simulated" data-testid="filter-simulated">
        <option value="">{{ zhCN.instance.simulated }}</option>
        <option value="0">0</option>
        <option value="1">1</option>
      </select>
      <button type="button" data-testid="instance-query" @click="load">{{ zhCN.common.query }}</button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="instance-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>{{ zhCN.instance.taskId }}</th>
          <th>{{ zhCN.instance.userId }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.instance.cycleKey }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.id }}</td>
          <td>{{ row.taskId }}</td>
          <td>{{ row.userId }}</td>
          <td>{{ row.status }}</td>
          <td>{{ row.cycleKey }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.TASK_INSTANCE_QUERY" type="button" data-testid="instance-detail" @click="openDetail(row)">
              {{ zhCN.instance.detail }}
            </button>
            <button
              v-if="row.status === INSTANCE_STATUS.IN_PROGRESS"
              v-auth="PERMS.TASK_INSTANCE_ABANDON"
              type="button"
              data-testid="instance-abandon"
              @click="openAbandon(row)"
            >
              {{ zhCN.instance.abandon }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <button type="button" :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</button>
      <span>{{ page }}</span>
      <button type="button" :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</button>
    </div>
    <div v-if="detail" data-testid="instance-detail-panel">
      <h3>{{ zhCN.instance.steps }}</h3>
      <table class="data-table">
        <tbody>
          <tr v-for="step in detail.steps ?? []" :key="step.stepCode">
            <td>{{ step.stepCode }}</td>
            <td>{{ step.type }}</td>
            <td>{{ step.status }}</td>
            <td>{{ step.progressCurrent }}/{{ step.progressTarget ?? "-" }}</td>
          </tr>
        </tbody>
      </table>
      <h3>{{ zhCN.instance.events }}</h3>
      <ul>
        <li v-for="(event, index) in detail.events ?? []" :key="`${event.code}-${index}`">
          {{ formatDateTime(event.time) }} {{ event.code }}
        </li>
      </ul>
    </div>
    <FormDialog
      :visible="abandonOpen"
      :title="zhCN.instance.abandon"
      @submit="submitAbandon"
      @cancel="abandonOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.instance.reason }}</span>
        <input v-model="reason" data-testid="abandon-reason" required />
      </label>
    </FormDialog>
  </section>
</template>
