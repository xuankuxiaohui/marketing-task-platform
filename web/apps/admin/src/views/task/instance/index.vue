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
    <div class="admin-page__header">
      <h2>{{ zhCN.instance.title }}</h2>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.taskId" data-testid="filter-task-id" :placeholder="zhCN.instance.taskId" />
      <el-input v-model="filters.userId" data-testid="filter-user-id" :placeholder="zhCN.instance.userId" />
      <el-select v-model="filters.status" data-testid="filter-status">
        <el-option value="" :label="zhCN.common.status" />
        <el-option v-for="item in Object.values(INSTANCE_STATUS)" :key="item" :value="item" :label="adminStatusLabel(item)" />
      </el-select>
      <el-select v-model="filters.simulated" data-testid="filter-simulated">
        <el-option value="" :label="zhCN.instance.simulated" />
        <el-option value="0" label="0" />
        <el-option value="1" label="1" />
      </el-select>
      <el-button data-testid="instance-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="instance-table" size="small" stripe>
      <el-table-column label="ID">
        <template #default="{ row }">{{ row.id }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.instance.taskId">
        <template #default="{ row }">{{ row.taskId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.instance.userId">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'info'"
            :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'"
          >
            {{ adminStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="zhCN.instance.cycleKey">
        <template #default="{ row }">{{ row.cycleKey }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.TASK_INSTANCE_QUERY" data-testid="instance-detail" @click="openDetail(row)">
              {{ zhCN.instance.detail }}
            </el-button>
            <el-button text
              v-if="row.status === INSTANCE_STATUS.IN_PROGRESS"
              v-auth="PERMS.TASK_INSTANCE_ABANDON"
              data-testid="instance-abandon"
              @click="openAbandon(row)"
            >
              {{ zhCN.instance.abandon }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.prevPage }}</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.nextPage }}</el-button>
    </div>
    <div v-if="detail" data-testid="instance-detail-panel">
      <h3>{{ zhCN.instance.steps }}</h3>
      <el-table :data="detail.steps ?? []" class="data-table admin-table" size="small" stripe>
      <el-table-column>
        <template #default="{ row }">{{ row.stepCode }}</template>
      </el-table-column>
      <el-table-column>
        <template #default="{ row }">{{ row.type }}</template>
      </el-table-column>
      <el-table-column>
        <template #default="{ row }">{{ adminStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column>
        <template #default="{ row }">{{ row.progressCurrent }}/{{ row.progressTarget ?? "-" }}</template>
      </el-table-column>
    </el-table>
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
      <el-form-item :label="zhCN.instance.reason">
        <el-input v-model="reason" data-testid="abandon-reason" required />
      </el-form-item>
    </FormDialog>
  </section>
</template>
