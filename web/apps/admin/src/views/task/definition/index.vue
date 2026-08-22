<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import {
  cancelSchedule,
  copyDefinition,
  deleteDefinition,
  offlineDefinition,
  pageDefinitions,
  pageScheduleFailures,
  publishDefinition,
  scheduleDefinition,
  type PublishResponse,
  type ScheduleFailureView,
  type TaskDefinitionView,
} from "@/api/task";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { DEFINITION_STATUS } from "@/constants/task";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { formatPublishImpact, isPublishPreview } from "@/utils/publish-confirm";

defineOptions({ name: "TaskDefinitionPage" });

const router = useRouter();
const records = ref<TaskDefinitionView[]>([]);
const failures = ref<ScheduleFailureView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ code: "", name: "", status: "", category: "" });
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);
const copyOpen = ref(false);
const scheduleOpen = ref(false);
const saving = ref(false);
const editing = ref<TaskDefinitionView | null>(null);
const copyForm = reactive({ code: "", name: "" });
const scheduleAt = ref("");

function isLiveStatus(status: string | undefined): boolean {
  return status === DEFINITION_STATUS.PUBLISHED || status === DEFINITION_STATUS.SCHEDULED;
}

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageDefinitions({
    code: filters.code,
    name: filters.name,
    status: filters.status,
    category: filters.category,
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

async function resetFilters(): Promise<void> {
  filters.code = "";
  filters.name = "";
  filters.status = "";
  filters.category = "";
  page.value = 1;
  await load();
}

async function loadFailures(): Promise<void> {
  const result = await pageScheduleFailures({ page: 1, pageSize: 20 });
  const parsed = okOrFeedback(result);
  if (parsed.ok) {
    failures.value = parsed.data?.records ?? [];
  }
}

function goCreate(): void {
  void router.push("/task/definitions/edit");
}

function goEdit(row: TaskDefinitionView): void {
  if (row.id == null) {
    return;
  }
  void router.push(`/task/definitions/edit/${row.id}`);
}

function goVersions(row: TaskDefinitionView): void {
  if (row.id == null) {
    return;
  }
  void router.push(`/task/definitions/${row.id}/versions`);
}

async function applyPublish(id: number, body: { confirm: boolean; early?: boolean }): Promise<boolean> {
  const result = await publishDefinition(id, body);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return false;
  }
  if (isPublishPreview(parsed.data)) {
    confirm.value = {
      message: formatPublishImpact(parsed.data as PublishResponse),
      run: async () => {
        const done = await publishDefinition(id, { confirm: true, early: body.early });
        const doneParsed = okOrFeedback(done);
        if (!doneParsed.ok) {
          feedback.value = doneParsed.feedback;
          return;
        }
        await load();
      },
    };
    return false;
  }
  return true;
}

async function onPublish(row: TaskDefinitionView, early = false): Promise<void> {
  if (row.id == null) {
    return;
  }
  feedback.value = null;
  const ok = await applyPublish(row.id, { confirm: false, early: early || undefined });
  if (ok) {
    await load();
  }
}

function openCopy(row: TaskDefinitionView): void {
  editing.value = row;
  copyForm.code = "";
  copyForm.name = "";
  copyOpen.value = true;
}

function openSchedule(row: TaskDefinitionView): void {
  editing.value = row;
  scheduleAt.value = "";
  scheduleOpen.value = true;
}

async function submitCopy(): Promise<void> {
  if (editing.value?.id == null) {
    return;
  }
  saving.value = true;
  const result = await copyDefinition(editing.value.id, { code: copyForm.code, name: copyForm.name });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  copyOpen.value = false;
  await load();
}

async function submitSchedule(): Promise<void> {
  if (editing.value?.id == null) {
    return;
  }
  saving.value = true;
  const result = await scheduleDefinition(editing.value.id, { publishAt: scheduleAt.value });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  scheduleOpen.value = false;
  await load();
}

async function onCancelSchedule(row: TaskDefinitionView): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await cancelSchedule(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

async function onOffline(row: TaskDefinitionView): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await offlineDefinition(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

function askDelete(row: TaskDefinitionView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteDefinition(row.id as number);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

async function onConfirm(): Promise<void> {
  const current = confirm.value;
  confirm.value = null;
  await current?.run();
}

onMounted(async () => {
  await load();
  await loadFailures();
});
</script>

<template>
  <section class="admin-page" data-testid="task-definition-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.task.title }}</h2>
      <el-button v-auth="PERMS.TASK_DEF_CREATE" type="primary" data-testid="task-create" @click="goCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.code" data-testid="filter-code" :placeholder="zhCN.task.code" />
      <el-input v-model="filters.name" data-testid="filter-name" :placeholder="zhCN.task.name" />
      <el-select v-model="filters.status" data-testid="filter-status">
        <el-option value="" :label="zhCN.common.status" />
        <el-option v-for="status in Object.values(DEFINITION_STATUS)" :key="status" :value="status" :label="status" />
      </el-select>
      <el-input v-model="filters.category" data-testid="filter-category" :placeholder="zhCN.task.category" />
      <el-button data-testid="task-query" @click="load">{{ zhCN.common.query }}</el-button>
      <el-button data-testid="task-reset" @click="resetFilters">{{ zhCN.common.reset }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.TASK_DEF_CREATE" text type="primary" @click="goCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table task-table" data-testid="task-table" size="small" stripe>
      <el-table-column :label="zhCN.task.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.task.name">
        <template #default="{ row }">{{ row.name }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">
          <el-tag size="small" :type="isLiveStatus(row.status) ? 'success' : 'info'" :class="{ 'status-tag--live': isLiveStatus(row.status) }">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="zhCN.task.version">
        <template #default="{ row }">{{ row.version }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.TASK_DEF_UPDATE" data-testid="task-edit" @click="goEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button text v-auth="PERMS.TASK_DEF_QUERY" data-testid="task-versions" @click="goVersions(row)">
              {{ zhCN.task.versionTitle }}
            </el-button>
            <el-button text v-auth="PERMS.TASK_DEF_PUBLISH" data-testid="task-publish" @click="onPublish(row)">
              {{ zhCN.task.publish }}
            </el-button>
            <el-button
              text
              v-if="row.status === DEFINITION_STATUS.SCHEDULED"
              v-auth="PERMS.TASK_DEF_PUBLISH"
              data-testid="task-publish-early"
              @click="onPublish(row, true)"
            >
              {{ zhCN.task.publishEarly }}
            </el-button>
            <el-button
              text
              v-if="row.status === DEFINITION_STATUS.DRAFT"
              v-auth="PERMS.TASK_DEF_SCHEDULE"
              data-testid="task-schedule"
              @click="openSchedule(row)"
            >
              {{ zhCN.task.schedule }}
            </el-button>
            <el-button
              text
              v-if="row.status === DEFINITION_STATUS.SCHEDULED"
              v-auth="PERMS.TASK_DEF_SCHEDULE"
              data-testid="task-cancel-schedule"
              @click="onCancelSchedule(row)"
            >
              {{ zhCN.task.cancelSchedule }}
            </el-button>
            <el-button
              text
              v-if="row.status === DEFINITION_STATUS.PUBLISHED"
              v-auth="PERMS.TASK_DEF_OFFLINE"
              data-testid="task-offline"
              @click="onOffline(row)"
            >
              {{ zhCN.task.offline }}
            </el-button>
            <el-button text v-auth="PERMS.TASK_DEF_COPY" data-testid="task-copy" @click="openCopy(row)">
              {{ zhCN.task.copy }}
            </el-button>
            <el-button
              text
              v-if="row.status === DEFINITION_STATUS.DRAFT"
              v-auth="PERMS.TASK_DEF_DELETE"
              data-testid="task-delete"
              @click="askDelete(row)"
            >
              {{ zhCN.common.delete }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</el-button>
    </div>
    <h3>{{ zhCN.task.scheduleFailures }}</h3>
    <p v-if="failures.length === 0" data-testid="failure-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="failures" class="data-table" data-testid="failure-table" stripe>
      <el-table-column :label="zhCN.task.code">
        <template #default="{ row }">{{ row.taskCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.task.reason">
        <template #default="{ row }">{{ row.reason }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.createdAt">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <FormDialog :visible="copyOpen" :title="zhCN.task.copy" :saving="saving" @submit="submitCopy" @cancel="copyOpen = false">
      <el-form-item :label="zhCN.task.newCode">
        <el-input v-model="copyForm.code" data-testid="copy-code" required />
      </el-form-item>
      <el-form-item :label="zhCN.task.newName">
        <el-input v-model="copyForm.name" data-testid="copy-name" required />
      </el-form-item>
    </FormDialog>
    <FormDialog
      :visible="scheduleOpen"
      :title="zhCN.task.schedule"
      :saving="saving"
      @submit="submitSchedule"
      @cancel="scheduleOpen = false"
    >
      <el-form-item :label="zhCN.task.publishAt">
        <el-input v-model="scheduleAt" data-testid="schedule-at" type="datetime-local" required />
      </el-form-item>
    </FormDialog>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>

<style scoped>
.admin-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.page-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: var(--admin-muted);
  font-size: 12px;
}
.task-table :deep(.el-table__header th.el-table__cell) {
  background: #f6f1e7;
  font-size: 12px;
  font-weight: 500;
  color: var(--admin-ink);
}
.task-table :deep(.el-table__row) {
  height: var(--admin-row);
}
.task-table :deep(.el-table td.el-table__cell),
.task-table :deep(.el-table th.el-table__cell) {
  border-right: none;
}
.task-table :deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: #faf7f0;
}
.status-tag--live {
  --el-tag-bg-color: #ccfbf1;
  --el-tag-border-color: #99f6e4;
  --el-tag-text-color: #0f766e;
}
</style>
