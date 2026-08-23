<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  deleteActivity,
  offlineActivity,
  pageActivities,
  publishActivity,
  saveActivity,
  scheduleActivity,
  type ActivityView,
} from "@/api/activity";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "ActivityManagePage" });

const records = ref<ActivityView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ code: "", name: "", status: "" });
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<ActivityView | null>(null);
const form = reactive({
  code: "",
  name: "",
  startTime: "",
  endTime: "",
  richText: "<p></p>",
  prizeId: "",
  allowUserIds: "",
  newUserOnly: false,
  newUserDays: "7",
  userDailyLimit: "",
  userTotalLimit: "",
  globalDailyLimit: "",
  regions: "",
  submodules: "",
  publishAt: "",
});
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageActivities({
    code: filters.code || undefined,
    name: filters.name || undefined,
    status: filters.status || undefined,
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

function openCreate(): void {
  editing.value = null;
  form.code = "";
  form.name = "";
  form.startTime = "";
  form.endTime = "";
  form.richText = "<p></p>";
  form.prizeId = "";
  form.allowUserIds = "";
  form.newUserOnly = false;
  form.newUserDays = "7";
  form.userDailyLimit = "";
  form.userTotalLimit = "";
  form.globalDailyLimit = "";
  form.regions = "";
  form.submodules = "";
  form.publishAt = "";
  formOpen.value = true;
}

function openEdit(row: ActivityView): void {
  editing.value = row;
  form.code = row.code ?? "";
  form.name = row.name ?? "";
  form.startTime = row.startTime ? row.startTime.slice(0, 16) : "";
  form.endTime = row.endTime ? row.endTime.slice(0, 16) : "";
  form.richText = row.richText ?? "<p></p>";
  form.prizeId = row.participationPrizeId != null ? String(row.participationPrizeId) : "";
  form.allowUserIds = (row.allowUserIds ?? []).join(",");
  form.newUserOnly = row.newUserOnly;
  form.newUserDays = String(row.newUserDays ?? 7);
  form.userDailyLimit = row.userDailyLimit != null ? String(row.userDailyLimit) : "";
  form.userTotalLimit = row.userTotalLimit != null ? String(row.userTotalLimit) : "";
  form.globalDailyLimit = row.globalDailyLimit != null ? String(row.globalDailyLimit) : "";
  form.regions = (row.regions ?? []).join(",");
  form.submodules = (row.submodules ?? []).map((item) => `${item.type},${item.refId}`).join("\n");
  form.publishAt = "";
  formOpen.value = true;
}

function toInstant(value: string): string {
  if (!value) {
    return value;
  }
  return value.length === 16 ? `${value}:00Z` : value;
}

function optionalNumber(raw: string): number | undefined {
  if (!raw.trim()) {
    return undefined;
  }
  return Number(raw);
}

function parseSubmodules(raw: string): { type: string; refId: number; sort: number }[] {
  return raw
    .split(/[\n,]+/)
    .map((row) => row.trim())
    .filter(Boolean)
    .map((row, index) => {
      const [type, refId] = row.split(/[:：\s]+/);
      return { type: type?.trim() ?? "TASK", refId: Number(refId), sort: index };
    })
    .filter((row) => row.type && Number.isFinite(row.refId));
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const body = {
    id: editing.value?.id,
    code: form.code,
    name: form.name,
    startTime: toInstant(form.startTime),
    endTime: toInstant(form.endTime),
    richText: form.richText,
    gray: { type: "NONE" },
    submodules: parseSubmodules(form.submodules),
    participationPrizeId: optionalNumber(form.prizeId),
    allowUserIds: form.allowUserIds
      .split(/[,\s]+/)
      .map(Number)
      .filter((id) => Number.isFinite(id) && id > 0),
    newUserOnly: form.newUserOnly,
    newUserDays: optionalNumber(form.newUserDays) ?? 7,
    userDailyLimit: optionalNumber(form.userDailyLimit),
    userTotalLimit: optionalNumber(form.userTotalLimit),
    globalDailyLimit: optionalNumber(form.globalDailyLimit),
    regions: form.regions
      .split(/[,\s]+/)
      .map((item) => item.trim())
      .filter(Boolean),
  };
  const result: Result = await saveActivity(body);
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function onPublish(row: ActivityView, confirmFlag: boolean): Promise<void> {
  const result = await publishActivity(row.id, { confirm: confirmFlag, early: true });
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  if (parsed.data?.requiresConfirm && !confirmFlag) {
    confirm.value = {
      message: parsed.data.message ?? zhCN.activity.revisionHint,
      run: async () => {
        await onPublish(row, true);
      },
    };
    return;
  }
  await load();
}

function askDelete(row: ActivityView): void {
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteActivity(row.id);
      const parsed = writeOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

async function onOffline(row: ActivityView): Promise<void> {
  const result = await offlineActivity(row.id);
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

async function onSchedule(row: ActivityView): Promise<void> {
  if (!form.publishAt) {
    return;
  }
  const result = await scheduleActivity(row.id, { publishAt: toInstant(form.publishAt) });
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

async function onConfirm(): Promise<void> {
  const current = confirm.value;
  confirm.value = null;
  await current?.run();
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
  <section class="admin-page" data-testid="activity-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.activity.title }}</h2>
      <a-button type="primary" v-auth="PERMS.ACTIVITY_CREATE" data-testid="activity-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.code" data-testid="filter-code" :placeholder="zhCN.activity.code" />
      <a-input v-model:value="filters.name" data-testid="filter-name" :placeholder="zhCN.activity.name" />
      <a-select v-model:value="filters.status" data-testid="filter-status">
        <a-select-option value="">{{ zhCN.common.status }}</a-select-option>
        <a-select-option value="DRAFT">{{ adminStatusLabel('DRAFT') }}</a-select-option>
        <a-select-option value="SCHEDULED">{{ adminStatusLabel('SCHEDULED') }}</a-select-option>
        <a-select-option value="PUBLISHED">{{ adminStatusLabel('PUBLISHED') }}</a-select-option>
        <a-select-option value="OFFLINE">{{ adminStatusLabel('OFFLINE') }}</a-select-option>
      </a-select>
      <a-button type="primary" data-testid="activity-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="activity-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.ACTIVITY_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.activity.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.activity.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.activity.version">
        <template #default="{ record: row }">{{ row.version }}{{ row.pendingRevision ? "*" : "" }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.ACTIVITY_UPDATE" data-testid="activity-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" v-auth="PERMS.ACTIVITY_PUBLISH" data-testid="activity-publish" @click="onPublish(row, false)">
              {{ zhCN.activity.publish }}
            </a-button>
            <a-button size="small" v-if="row.status === 'PUBLISHED'" v-auth="PERMS.ACTIVITY_OFFLINE" data-testid="activity-offline" @click="onOffline(row)">
              {{ zhCN.activity.offline }}
            </a-button>
            <a-button size="small" danger v-if="row.status === 'DRAFT'" v-auth="PERMS.ACTIVITY_DELETE" data-testid="activity-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <a-form-item :label="zhCN.activity.code">
        <a-input v-model:value="form.code" data-testid="activity-code" :disabled="editing != null" required />
      </a-form-item>
      <a-form-item :label="zhCN.activity.name">
        <a-input v-model:value="form.name" data-testid="activity-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.activity.startTime">
        <a-date-picker v-model:value="form.startTime" data-testid="activity-start" required show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
      <a-form-item :label="zhCN.activity.endTime">
        <a-date-picker v-model:value="form.endTime" data-testid="activity-end" required show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
      <a-form-item :label="zhCN.activity.richText">
        <a-textarea v-model:value="form.richText" data-testid="activity-html" :rows="6" required />
      </a-form-item>
      <a-form-item :label="zhCN.activity.prizeId">
        <a-input v-model:value="form.prizeId" data-testid="activity-prize" />
      </a-form-item>
      <a-form-item :label="zhCN.activity.globalDailyLimit">
        <a-input v-model:value="form.globalDailyLimit" data-testid="activity-global-limit" />
      </a-form-item>
      <a-form-item :label="zhCN.activity.allowUserIds">
        <a-input v-model:value="form.allowUserIds" data-testid="activity-allow-users" />
      </a-form-item>
      <a-form-item :label="zhCN.activity.submodules">
        <a-textarea v-model:value="form.submodules" data-testid="activity-submodules" />
      </a-form-item>
      <a-form-item v-if="editing?.status === 'DRAFT'" :label="zhCN.activity.publishAt">
        <a-date-picker v-model:value="form.publishAt" data-testid="activity-schedule-at" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
        <a-button v-auth="PERMS.ACTIVITY_PUBLISH" data-testid="activity-schedule" @click="editing && onSchedule(editing)">
          {{ zhCN.activity.schedule }}
        </a-button>
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
