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
import { adminStatusLabel } from "@/utils/status-label";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { formatPublishImpact, isPublishPreview } from "@/utils/publish-confirm";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "TaskDefinitionPage" });

const router = useRouter();
const records = ref<TaskDefinitionView[]>([]);
const failures = ref<ScheduleFailureView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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
  const result = await pageScheduleFailures({ page: 1, pageSize: ADMIN_PAGE_SIZE });
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

function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}
</script>

<template>
  <section class="admin-page" data-testid="task-definition-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.task.title }}</h2>
      <a-button v-auth="PERMS.TASK_DEF_CREATE" type="primary" data-testid="task-create" @click="goCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.code" data-testid="filter-code" :placeholder="zhCN.task.code" />
      <a-input v-model:value="filters.name" data-testid="filter-name" :placeholder="zhCN.task.name" />
      <a-select v-model:value="filters.status" data-testid="filter-status">
        <a-select-option value="">{{ zhCN.common.status }}</a-select-option>
        <a-select-option v-for="status in Object.values(DEFINITION_STATUS)" :key="status" :value="status">{{ adminStatusLabel(status) }}</a-select-option>
      </a-select>
      <a-input v-model:value="filters.category" data-testid="filter-category" :placeholder="zhCN.task.category" />
      <a-button type="primary" data-testid="task-query" @click="load">{{ zhCN.common.query }}</a-button>
      <a-button data-testid="task-reset" @click="resetFilters">{{ zhCN.common.reset }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table task-table admin-table" data-testid="task-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.TASK_DEF_CREATE" type="primary" size="small" @click="goCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.task.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.task.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="isLiveStatus(row.status) ? 'success' : 'default'" :class="{ 'status-tag--live': isLiveStatus(row.status) }">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.task.version">
        <template #default="{ record: row }">{{ row.version }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.TASK_DEF_UPDATE" data-testid="task-edit" @click="goEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" v-auth="PERMS.TASK_DEF_QUERY" data-testid="task-versions" @click="goVersions(row)">
              {{ zhCN.task.versionTitle }}
            </a-button>
            <a-button size="small" v-auth="PERMS.TASK_DEF_PUBLISH" data-testid="task-publish" @click="onPublish(row)">
              {{ zhCN.task.publish }}
            </a-button>
            <a-button size="small" v-if="row.status === DEFINITION_STATUS.SCHEDULED" v-auth="PERMS.TASK_DEF_PUBLISH" data-testid="task-publish-early" @click="onPublish(row, true)">
              {{ zhCN.task.publishEarly }}
            </a-button>
            <a-button size="small" v-if="row.status === DEFINITION_STATUS.DRAFT" v-auth="PERMS.TASK_DEF_SCHEDULE" data-testid="task-schedule" @click="openSchedule(row)">
              {{ zhCN.task.schedule }}
            </a-button>
            <a-button size="small" v-if="row.status === DEFINITION_STATUS.SCHEDULED" v-auth="PERMS.TASK_DEF_SCHEDULE" data-testid="task-cancel-schedule" @click="onCancelSchedule(row)">
              {{ zhCN.task.cancelSchedule }}
            </a-button>
            <a-button size="small" v-if="row.status === DEFINITION_STATUS.PUBLISHED" v-auth="PERMS.TASK_DEF_OFFLINE" data-testid="task-offline" @click="onOffline(row)">
              {{ zhCN.task.offline }}
            </a-button>
            <a-button size="small" v-auth="PERMS.TASK_DEF_COPY" data-testid="task-copy" @click="openCopy(row)">
              {{ zhCN.task.copy }}
            </a-button>
            <a-button size="small" danger v-if="row.status === DEFINITION_STATUS.DRAFT" v-auth="PERMS.TASK_DEF_DELETE" data-testid="task-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <h3>{{ zhCN.task.scheduleFailures }}</h3>
    <p v-if="failures.length === 0" data-testid="failure-empty">{{ zhCN.common.empty }}</p>
    <a-table v-else :data-source="failures" class="data-table admin-table" data-testid="failure-table" size="small" :pagination="false" :row-key="adminRowKey">
      <a-table-column :title="zhCN.task.code">
        <template #default="{ record: row }">{{ row.taskCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.task.reason">
        <template #default="{ record: row }">{{ row.reason }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
    </a-table>
    <FormDialog :visible="copyOpen" :title="zhCN.task.copy" :saving="saving" @submit="submitCopy" @cancel="copyOpen = false">
      <a-form-item :label="zhCN.task.newCode">
        <a-input v-model:value="copyForm.code" data-testid="copy-code" required />
      </a-form-item>
      <a-form-item :label="zhCN.task.newName">
        <a-input v-model:value="copyForm.name" data-testid="copy-name" required />
      </a-form-item>
    </FormDialog>
    <FormDialog
      :visible="scheduleOpen"
      :title="zhCN.task.schedule"
      :saving="saving"
      @submit="submitSchedule"
      @cancel="scheduleOpen = false"
    >
      <a-form-item :label="zhCN.task.publishAt">
        <a-date-picker v-model:value="scheduleAt" data-testid="schedule-at" required show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
    </FormDialog>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>
