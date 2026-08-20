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
    <h2>{{ zhCN.task.title }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.code" data-testid="filter-code" :placeholder="zhCN.task.code" />
      <input v-model="filters.name" data-testid="filter-name" :placeholder="zhCN.task.name" />
      <select v-model="filters.status" data-testid="filter-status">
        <option value="">{{ zhCN.common.status }}</option>
        <option v-for="status in Object.values(DEFINITION_STATUS)" :key="status" :value="status">{{ status }}</option>
      </select>
      <input v-model="filters.category" data-testid="filter-category" :placeholder="zhCN.task.category" />
      <button type="button" data-testid="task-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.TASK_DEF_CREATE" type="button" data-testid="task-create" @click="goCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="task-table">
      <thead>
        <tr>
          <th>{{ zhCN.task.code }}</th>
          <th>{{ zhCN.task.name }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.task.version }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.status }}</td>
          <td>{{ row.version }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.TASK_DEF_UPDATE" type="button" data-testid="task-edit" @click="goEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button v-auth="PERMS.TASK_DEF_QUERY" type="button" data-testid="task-versions" @click="goVersions(row)">
              {{ zhCN.task.versionTitle }}
            </button>
            <button v-auth="PERMS.TASK_DEF_PUBLISH" type="button" data-testid="task-publish" @click="onPublish(row)">
              {{ zhCN.task.publish }}
            </button>
            <button
              v-if="row.status === DEFINITION_STATUS.SCHEDULED"
              v-auth="PERMS.TASK_DEF_PUBLISH"
              type="button"
              data-testid="task-publish-early"
              @click="onPublish(row, true)"
            >
              {{ zhCN.task.publishEarly }}
            </button>
            <button
              v-if="row.status === DEFINITION_STATUS.DRAFT"
              v-auth="PERMS.TASK_DEF_SCHEDULE"
              type="button"
              data-testid="task-schedule"
              @click="openSchedule(row)"
            >
              {{ zhCN.task.schedule }}
            </button>
            <button
              v-if="row.status === DEFINITION_STATUS.SCHEDULED"
              v-auth="PERMS.TASK_DEF_SCHEDULE"
              type="button"
              data-testid="task-cancel-schedule"
              @click="onCancelSchedule(row)"
            >
              {{ zhCN.task.cancelSchedule }}
            </button>
            <button
              v-if="row.status === DEFINITION_STATUS.PUBLISHED"
              v-auth="PERMS.TASK_DEF_OFFLINE"
              type="button"
              data-testid="task-offline"
              @click="onOffline(row)"
            >
              {{ zhCN.task.offline }}
            </button>
            <button v-auth="PERMS.TASK_DEF_COPY" type="button" data-testid="task-copy" @click="openCopy(row)">
              {{ zhCN.task.copy }}
            </button>
            <button
              v-if="row.status === DEFINITION_STATUS.DRAFT"
              v-auth="PERMS.TASK_DEF_DELETE"
              type="button"
              data-testid="task-delete"
              @click="askDelete(row)"
            >
              {{ zhCN.common.delete }}
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
    <h3>{{ zhCN.task.scheduleFailures }}</h3>
    <p v-if="failures.length === 0" data-testid="failure-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="failure-table">
      <thead>
        <tr>
          <th>{{ zhCN.task.code }}</th>
          <th>{{ zhCN.task.reason }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in failures" :key="row.id">
          <td>{{ row.taskCode }}</td>
          <td>{{ row.reason }}</td>
          <td>{{ formatDateTime(row.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
    <FormDialog :visible="copyOpen" :title="zhCN.task.copy" :saving="saving" @submit="submitCopy" @cancel="copyOpen = false">
      <label class="field">
        <span>{{ zhCN.task.newCode }}</span>
        <input v-model="copyForm.code" data-testid="copy-code" required />
      </label>
      <label class="field">
        <span>{{ zhCN.task.newName }}</span>
        <input v-model="copyForm.name" data-testid="copy-name" required />
      </label>
    </FormDialog>
    <FormDialog
      :visible="scheduleOpen"
      :title="zhCN.task.schedule"
      :saving="saving"
      @submit="submitSchedule"
      @cancel="scheduleOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.task.publishAt }}</span>
        <input v-model="scheduleAt" data-testid="schedule-at" type="datetime-local" required />
      </label>
    </FormDialog>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>
