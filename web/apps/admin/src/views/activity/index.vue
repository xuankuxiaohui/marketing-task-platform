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
    <div class="admin-toolbar">
      <input v-model="filters.code" data-testid="filter-code" :placeholder="zhCN.activity.code" />
      <input v-model="filters.name" data-testid="filter-name" :placeholder="zhCN.activity.name" />
      <select v-model="filters.status" data-testid="filter-status">
        <option value="">{{ zhCN.common.status }}</option>
        <option value="DRAFT">DRAFT</option>
        <option value="SCHEDULED">SCHEDULED</option>
        <option value="PUBLISHED">PUBLISHED</option>
        <option value="OFFLINE">OFFLINE</option>
      </select>
      <button type="button" data-testid="activity-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.ACTIVITY_CREATE" type="button" data-testid="activity-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="activity-table">
      <thead>
        <tr>
          <th>{{ zhCN.activity.code }}</th>
          <th>{{ zhCN.activity.name }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.activity.version }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.status }}</td>
          <td>{{ row.version }}{{ row.pendingRevision ? "*" : "" }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.ACTIVITY_UPDATE" type="button" data-testid="activity-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button v-auth="PERMS.ACTIVITY_PUBLISH" type="button" data-testid="activity-publish" @click="onPublish(row, false)">
              {{ zhCN.activity.publish }}
            </button>
            <button
              v-if="row.status === 'PUBLISHED'"
              v-auth="PERMS.ACTIVITY_OFFLINE"
              type="button"
              data-testid="activity-offline"
              @click="onOffline(row)"
            >
              {{ zhCN.activity.offline }}
            </button>
            <button
              v-if="row.status === 'DRAFT'"
              v-auth="PERMS.ACTIVITY_DELETE"
              type="button"
              data-testid="activity-delete"
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
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.activity.code }}</span>
        <input v-model="form.code" data-testid="activity-code" :disabled="editing != null" required />
      </label>
      <label class="field">
        <span>{{ zhCN.activity.name }}</span>
        <input v-model="form.name" data-testid="activity-name" required />
      </label>
      <label class="field">
        <span>{{ zhCN.activity.startTime }}</span>
        <input v-model="form.startTime" type="datetime-local" data-testid="activity-start" required />
      </label>
      <label class="field">
        <span>{{ zhCN.activity.endTime }}</span>
        <input v-model="form.endTime" type="datetime-local" data-testid="activity-end" required />
      </label>
      <label class="field">
        <span>{{ zhCN.activity.richText }}</span>
        <textarea v-model="form.richText" data-testid="activity-html" rows="6" required />
      </label>
      <label class="field">
        <span>{{ zhCN.activity.prizeId }}</span>
        <input v-model="form.prizeId" data-testid="activity-prize" />
      </label>
      <label class="field">
        <span>{{ zhCN.activity.globalDailyLimit }}</span>
        <input v-model="form.globalDailyLimit" data-testid="activity-global-limit" />
      </label>
      <label class="field">
        <span>{{ zhCN.activity.allowUserIds }}</span>
        <input v-model="form.allowUserIds" data-testid="activity-allow-users" />
      </label>
      <label class="field">
        <span>{{ zhCN.activity.submodules }}</span>
        <textarea v-model="form.submodules" data-testid="activity-submodules" />
      </label>
      <label v-if="editing?.status === 'DRAFT'" class="field">
        <span>{{ zhCN.activity.publishAt }}</span>
        <input v-model="form.publishAt" type="datetime-local" data-testid="activity-schedule-at" />
        <button
          v-auth="PERMS.ACTIVITY_PUBLISH"
          type="button"
          data-testid="activity-schedule"
          @click="editing && onSchedule(editing)"
        >
          {{ zhCN.activity.schedule }}
        </button>
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
