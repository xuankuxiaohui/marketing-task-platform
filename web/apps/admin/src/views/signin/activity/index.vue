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
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.code" data-testid="filter-code" :placeholder="zhCN.signin.code" />
      <el-input v-model="filters.name" data-testid="filter-name" :placeholder="zhCN.signin.name" />
      <el-select v-model="filters.status" data-testid="filter-status">
        <el-option value="" :label="zhCN.common.status" />
        <el-option value="DRAFT" label="DRAFT" />
        <el-option value="SCHEDULED" label="SCHEDULED" />
        <el-option value="PUBLISHED" label="PUBLISHED" />
        <el-option value="OFFLINE" label="OFFLINE" />
      </el-select>
      <el-button data-testid="signin-query" @click="load">{{ zhCN.common.query }}</el-button>
      <el-button v-auth="PERMS.SIGNIN_CONFIG_CREATE" data-testid="signin-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="records" class="data-table" data-testid="signin-table" stripe>
      <el-table-column :label="zhCN.signin.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.signin.name">
        <template #default="{ row }">{{ row.name }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.signin.version">
        <template #default="{ row }">{{ row.version }}{{ row.pendingRevision ? "*" : "" }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-auth="PERMS.SIGNIN_CONFIG_UPDATE" data-testid="signin-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button v-auth="PERMS.SIGNIN_CONFIG_PUBLISH" data-testid="signin-publish" @click="onPublish(row, false)">
              {{ zhCN.signin.publish }}
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              v-auth="PERMS.SIGNIN_CONFIG_OFFLINE"
              data-testid="signin-offline"
              @click="onOffline(row)"
            >
              {{ zhCN.signin.offline }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-auth="PERMS.SIGNIN_CONFIG_DELETE"
              data-testid="signin-delete"
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
      <el-form-item :label="zhCN.signin.code">
        <el-input v-model="form.code" data-testid="signin-code" :disabled="editing != null" required />
      </el-form-item>
      <el-form-item :label="zhCN.signin.name">
        <el-input v-model="form.name" data-testid="signin-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.signin.startTime">
        <el-input v-model="form.startTime" type="datetime-local" data-testid="signin-start" required />
      </el-form-item>
      <el-form-item :label="zhCN.signin.endTime">
        <el-input v-model="form.endTime" type="datetime-local" data-testid="signin-end" required />
      </el-form-item>
      <el-form-item :label="zhCN.signin.tiers">
        <el-input v-model="form.tiers" type="textarea" data-testid="signin-tiers" required  />
      </el-form-item>
      <el-form-item v-if="editing?.status === 'DRAFT'" :label="zhCN.signin.publishAt">
        <el-input v-model="form.publishAt" type="datetime-local" data-testid="signin-schedule-at" />
        <el-button
          v-auth="PERMS.SIGNIN_CONFIG_SCHEDULE"
          data-testid="signin-schedule"
          @click="editing && onSchedule(editing)"
        >
          {{ zhCN.signin.schedule }}
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
