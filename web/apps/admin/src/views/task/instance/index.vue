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
import { adminStatusLabel } from "@/utils/status-label";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "TaskInstancePage" });

const records = ref<AdminInstanceView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  abandonOpen.value = false;
  await load();
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
  <section class="admin-page" data-testid="instance-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.instance.title }}</h2>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.taskId" data-testid="filter-task-id" :placeholder="zhCN.instance.taskId" />
      <a-input v-model:value="filters.userId" data-testid="filter-user-id" :placeholder="zhCN.instance.userId" />
      <a-select v-model:value="filters.status" data-testid="filter-status">
        <a-select-option value="">{{ zhCN.common.status }}</a-select-option>
        <a-select-option v-for="item in Object.values(INSTANCE_STATUS)" :key="item" :value="item">{{ adminStatusLabel(item) }}</a-select-option>
      </a-select>
      <a-select v-model:value="filters.simulated" data-testid="filter-simulated">
        <a-select-option value="">{{ zhCN.instance.simulated }}</a-select-option>
        <a-select-option value="0">0</a-select-option>
        <a-select-option value="1">1</a-select-option>
      </a-select>
      <a-button type="primary" data-testid="instance-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="instance-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column title="ID">
        <template #default="{ record: row }">{{ row.id }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.instance.taskId">
        <template #default="{ record: row }">{{ row.taskId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.instance.userId">
        <template #default="{ record: row }">{{ row.userId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.instance.cycleKey">
        <template #default="{ record: row }">{{ row.cycleKey }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.TASK_INSTANCE_QUERY" data-testid="instance-detail" @click="openDetail(row)">
              {{ zhCN.instance.detail }}
            </a-button>
            <a-button size="small" v-if="row.status === INSTANCE_STATUS.IN_PROGRESS" v-auth="PERMS.TASK_INSTANCE_ABANDON" data-testid="instance-abandon" @click="openAbandon(row)">
              {{ zhCN.instance.abandon }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <div v-if="detail" data-testid="instance-detail-panel">
      <h3>{{ zhCN.instance.steps }}</h3>
      <a-table :data-source="detail.steps ?? []" class="data-table admin-table" size="small" :pagination="false" :row-key="adminRowKey">
      <a-table-column>
        <template #default="{ record: row }">{{ row.stepCode }}</template>
      </a-table-column>
      <a-table-column>
        <template #default="{ record: row }">{{ row.type }}</template>
      </a-table-column>
      <a-table-column>
        <template #default="{ record: row }">{{ adminStatusLabel(row.status) }}</template>
      </a-table-column>
      <a-table-column>
        <template #default="{ record: row }">{{ row.progressCurrent }}/{{ row.progressTarget ?? "-" }}</template>
      </a-table-column>
    </a-table>
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
      <a-form-item :label="zhCN.instance.reason">
        <a-input v-model:value="reason" data-testid="abandon-reason" required />
      </a-form-item>
    </FormDialog>
  </section>
</template>
