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
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "SigninActivityPage" });

const records = ref<SigninActivityView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="signin-activity-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.signin.activityTitle }}</h2>
      <a-button type="primary" v-auth="PERMS.SIGNIN_CONFIG_CREATE" data-testid="signin-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.code" data-testid="filter-code" :placeholder="zhCN.signin.code" />
      <a-input v-model:value="filters.name" data-testid="filter-name" :placeholder="zhCN.signin.name" />
      <a-select v-model:value="filters.status" data-testid="filter-status">
        <a-select-option value="">{{ zhCN.common.status }}</a-select-option>
        <a-select-option value="DRAFT">DRAFT</a-select-option>
        <a-select-option value="SCHEDULED">SCHEDULED</a-select-option>
        <a-select-option value="PUBLISHED">PUBLISHED</a-select-option>
        <a-select-option value="OFFLINE">OFFLINE</a-select-option>
      </a-select>
      <a-button type="primary" data-testid="signin-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="signin-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.SIGNIN_CONFIG_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.signin.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.signin.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.signin.version">
        <template #default="{ record: row }">{{ row.version }}{{ row.pendingRevision ? "*" : "" }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.SIGNIN_CONFIG_UPDATE" data-testid="signin-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" v-auth="PERMS.SIGNIN_CONFIG_PUBLISH" data-testid="signin-publish" @click="onPublish(row, false)">
              {{ zhCN.signin.publish }}
            </a-button>
            <a-button size="small" v-if="row.status === 'PUBLISHED'" v-auth="PERMS.SIGNIN_CONFIG_OFFLINE" data-testid="signin-offline" @click="onOffline(row)">
              {{ zhCN.signin.offline }}
            </a-button>
            <a-button size="small" danger v-if="row.status === 'DRAFT'" v-auth="PERMS.SIGNIN_CONFIG_DELETE" data-testid="signin-delete" @click="askDelete(row)">
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
      <a-form-item :label="zhCN.signin.code">
        <a-input v-model:value="form.code" data-testid="signin-code" :disabled="editing != null" required />
      </a-form-item>
      <a-form-item :label="zhCN.signin.name">
        <a-input v-model:value="form.name" data-testid="signin-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.signin.startTime">
        <a-date-picker v-model:value="form.startTime" data-testid="signin-start" required show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
      <a-form-item :label="zhCN.signin.endTime">
        <a-date-picker v-model:value="form.endTime" data-testid="signin-end" required show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
      <a-form-item :label="zhCN.signin.tiers">
        <a-textarea v-model:value="form.tiers" data-testid="signin-tiers" required />
      </a-form-item>
      <a-form-item v-if="editing?.status === 'DRAFT'" :label="zhCN.signin.publishAt">
        <a-date-picker v-model:value="form.publishAt" data-testid="signin-schedule-at" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
        <a-button v-auth="PERMS.SIGNIN_CONFIG_SCHEDULE" data-testid="signin-schedule" @click="editing && onSchedule(editing)">
          {{ zhCN.signin.schedule }}
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
