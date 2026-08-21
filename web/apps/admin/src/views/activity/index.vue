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
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "ActivityManagePage" });

const records = ref<ActivityView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function onPublish(row: ActivityView, confirmFlag: boolean): Promise<void> {
  const result = await publishActivity(row.id, { confirm: confirmFlag, early: true });
  const parsed = okOrFeedback(result);
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
      const parsed = okOrFeedback(result);
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
  const parsed = okOrFeedback(result);
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
  const parsed = okOrFeedback(result);
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

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="activity-page">
    <h2>{{ zhCN.activity.title }}</h2>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.code" data-testid="filter-code" :placeholder="zhCN.activity.code" />
      <el-input v-model="filters.name" data-testid="filter-name" :placeholder="zhCN.activity.name" />
      <el-select v-model="filters.status" data-testid="filter-status">
        <el-option value="" :label="zhCN.common.status" />
        <el-option value="DRAFT" label="DRAFT" />
        <el-option value="SCHEDULED" label="SCHEDULED" />
        <el-option value="PUBLISHED" label="PUBLISHED" />
        <el-option value="OFFLINE" label="OFFLINE" />
      </el-select>
      <el-button data-testid="activity-query" @click="load">{{ zhCN.common.query }}</el-button>
      <el-button v-auth="PERMS.ACTIVITY_CREATE" data-testid="activity-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="records" class="data-table" data-testid="activity-table" stripe>
      <el-table-column :label="zhCN.activity.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.activity.name">
        <template #default="{ row }">{{ row.name }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.activity.version">
        <template #default="{ row }">{{ row.version }}{{ row.pendingRevision ? "*" : "" }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-auth="PERMS.ACTIVITY_UPDATE" data-testid="activity-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button v-auth="PERMS.ACTIVITY_PUBLISH" data-testid="activity-publish" @click="onPublish(row, false)">
              {{ zhCN.activity.publish }}
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              v-auth="PERMS.ACTIVITY_OFFLINE"
              data-testid="activity-offline"
              @click="onOffline(row)"
            >
              {{ zhCN.activity.offline }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-auth="PERMS.ACTIVITY_DELETE"
              data-testid="activity-delete"
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
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <el-form-item :label="zhCN.activity.code">
        <el-input v-model="form.code" data-testid="activity-code" :disabled="editing != null" required />
      </el-form-item>
      <el-form-item :label="zhCN.activity.name">
        <el-input v-model="form.name" data-testid="activity-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.activity.startTime">
        <el-input v-model="form.startTime" type="datetime-local" data-testid="activity-start" required />
      </el-form-item>
      <el-form-item :label="zhCN.activity.endTime">
        <el-input v-model="form.endTime" type="datetime-local" data-testid="activity-end" required />
      </el-form-item>
      <el-form-item :label="zhCN.activity.richText">
        <el-input type="textarea" v-model="form.richText" data-testid="activity-html" :rows="6" required  />
      </el-form-item>
      <el-form-item :label="zhCN.activity.prizeId">
        <el-input v-model="form.prizeId" data-testid="activity-prize" />
      </el-form-item>
      <el-form-item :label="zhCN.activity.globalDailyLimit">
        <el-input v-model="form.globalDailyLimit" data-testid="activity-global-limit" />
      </el-form-item>
      <el-form-item :label="zhCN.activity.allowUserIds">
        <el-input v-model="form.allowUserIds" data-testid="activity-allow-users" />
      </el-form-item>
      <el-form-item :label="zhCN.activity.submodules">
        <el-input type="textarea" v-model="form.submodules" data-testid="activity-submodules"  />
      </el-form-item>
      <el-form-item v-if="editing?.status === 'DRAFT'" :label="zhCN.activity.publishAt">
        <el-input v-model="form.publishAt" type="datetime-local" data-testid="activity-schedule-at" />
        <el-button
          v-auth="PERMS.ACTIVITY_PUBLISH"
          data-testid="activity-schedule"
          @click="editing && onSchedule(editing)"
        >
          {{ zhCN.activity.schedule }}
        </el-button>
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
