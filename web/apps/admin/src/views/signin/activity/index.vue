<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  deleteSigninActivity,
  offlineSigninActivity,
  pageSigninActivities,
  publishSigninActivity,
  saveSigninActivity,
  scheduleSigninActivity,
  type SigninActivityView,
} from "@/api/signin";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "SigninActivityPage" });

const records = ref<SigninActivityView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ code: "", name: "", status: "" });
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<SigninActivityView | null>(null);
const form = reactive({
  code: "",
  name: "",
  startTime: "",
  endTime: "",
  tiers: "1,10",
  publishAt: "",
});
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageSigninActivities({
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

function parseTiers(raw: string): { day: number; prizeId: number }[] {
  return raw
    .split(/[\n,]+/)
    .map((row) => row.trim())
    .filter(Boolean)
    .map((row) => {
      const [day, prizeId] = row.split(/[:：\s]+/).map(Number);
      return { day, prizeId };
    });
}

function openCreate(): void {
  editing.value = null;
  form.code = "";
  form.name = "";
  form.startTime = "";
  form.endTime = "";
  form.tiers = "1,10";
  form.publishAt = "";
  formOpen.value = true;
}

function openEdit(row: SigninActivityView): void {
  editing.value = row;
  form.code = row.code ?? "";
  form.name = row.name ?? "";
  form.startTime = row.startTime ? row.startTime.slice(0, 16) : "";
  form.endTime = row.endTime ? row.endTime.slice(0, 16) : "";
  form.tiers = (row.tiers ?? []).map((tier) => `${tier.day},${tier.prizeId}`).join("\n");
  form.publishAt = "";
  formOpen.value = true;
}

function toInstant(value: string): string {
  if (!value) {
    return value;
  }
  return value.length === 16 ? `${value}:00Z` : value;
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
    tiers: parseTiers(form.tiers),
  };
  const result: Result = await saveSigninActivity(body);
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function onPublish(row: SigninActivityView, confirmFlag: boolean): Promise<void> {
  const result = await publishSigninActivity(row.id, { confirm: confirmFlag, early: true });
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  if (parsed.data?.requiresConfirm && !confirmFlag) {
    confirm.value = {
      message: parsed.data.message ?? zhCN.signin.revisionHint,
      run: async () => {
        await onPublish(row, true);
      },
    };
    return;
  }
  await load();
}

function askDelete(row: SigninActivityView): void {
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteSigninActivity(row.id);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

async function onOffline(row: SigninActivityView): Promise<void> {
  const result = await offlineSigninActivity(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

async function onSchedule(row: SigninActivityView): Promise<void> {
  if (!form.publishAt) {
    return;
  }
  const result = await scheduleSigninActivity(row.id, { publishAt: toInstant(form.publishAt) });
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
  <section class="admin-page" data-testid="signin-activity-page">
    <h2>{{ zhCN.signin.activityTitle }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.code" data-testid="filter-code" :placeholder="zhCN.signin.code" />
      <input v-model="filters.name" data-testid="filter-name" :placeholder="zhCN.signin.name" />
      <select v-model="filters.status" data-testid="filter-status">
        <option value="">{{ zhCN.common.status }}</option>
        <option value="DRAFT">DRAFT</option>
        <option value="SCHEDULED">SCHEDULED</option>
        <option value="PUBLISHED">PUBLISHED</option>
        <option value="OFFLINE">OFFLINE</option>
      </select>
      <button type="button" data-testid="signin-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.SIGNIN_CONFIG_CREATE" type="button" data-testid="signin-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="signin-table">
      <thead>
        <tr>
          <th>{{ zhCN.signin.code }}</th>
          <th>{{ zhCN.signin.name }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.signin.version }}</th>
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
            <button v-auth="PERMS.SIGNIN_CONFIG_UPDATE" type="button" data-testid="signin-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button v-auth="PERMS.SIGNIN_CONFIG_PUBLISH" type="button" data-testid="signin-publish" @click="onPublish(row, false)">
              {{ zhCN.signin.publish }}
            </button>
            <button
              v-if="row.status === 'PUBLISHED'"
              v-auth="PERMS.SIGNIN_CONFIG_OFFLINE"
              type="button"
              data-testid="signin-offline"
              @click="onOffline(row)"
            >
              {{ zhCN.signin.offline }}
            </button>
            <button
              v-if="row.status === 'DRAFT'"
              v-auth="PERMS.SIGNIN_CONFIG_DELETE"
              type="button"
              data-testid="signin-delete"
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
        <span>{{ zhCN.signin.code }}</span>
        <input v-model="form.code" data-testid="signin-code" :disabled="editing != null" required />
      </label>
      <label class="field">
        <span>{{ zhCN.signin.name }}</span>
        <input v-model="form.name" data-testid="signin-name" required />
      </label>
      <label class="field">
        <span>{{ zhCN.signin.startTime }}</span>
        <input v-model="form.startTime" type="datetime-local" data-testid="signin-start" required />
      </label>
      <label class="field">
        <span>{{ zhCN.signin.endTime }}</span>
        <input v-model="form.endTime" type="datetime-local" data-testid="signin-end" required />
      </label>
      <label class="field">
        <span>{{ zhCN.signin.tiers }}</span>
        <textarea v-model="form.tiers" data-testid="signin-tiers" required />
      </label>
      <label v-if="editing?.status === 'DRAFT'" class="field">
        <span>{{ zhCN.signin.publishAt }}</span>
        <input v-model="form.publishAt" type="datetime-local" data-testid="signin-schedule-at" />
        <button
          v-auth="PERMS.SIGNIN_CONFIG_SCHEDULE"
          type="button"
          data-testid="signin-schedule"
          @click="editing && onSchedule(editing)"
        >
          {{ zhCN.signin.schedule }}
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
