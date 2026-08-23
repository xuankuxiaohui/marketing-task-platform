<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  createCrowd,
  deleteCrowd,
  importCrowd,
  pageCrowds,
  updateCrowd,
  type CrowdResponse,
} from "@/api/task";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "TaskCrowdPage" });

const records = ref<CrowdResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const formOpen = ref(false);
const importOpen = ref(false);
const saving = ref(false);
const editing = ref<CrowdResponse | null>(null);
const form = reactive({ code: "", name: "", status: STATUS.ENABLED as string });
const importContent = ref("");
const importHint = ref("");
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageCrowds({ page: page.value, pageSize });
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
  form.status = STATUS.ENABLED;
  formOpen.value = true;
}

function openEdit(row: CrowdResponse): void {
  editing.value = row;
  form.code = row.code ?? "";
  form.name = row.name ?? "";
  form.status = row.status ?? STATUS.ENABLED;
  formOpen.value = true;
}

function openImport(row: CrowdResponse): void {
  editing.value = row;
  importContent.value = "";
  importHint.value = "";
  importOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const body = { code: form.code, name: form.name, status: form.status };
  const result: Result = editing.value?.id != null ? await updateCrowd(editing.value.id, body) : await createCrowd(body);
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function submitImport(): Promise<void> {
  if (editing.value?.id == null) {
    return;
  }
  saving.value = true;
  const result = await importCrowd(editing.value.id, { content: importContent.value });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  importHint.value = zhCN.crowd.importResult
    .replace("{imported}", String(parsed.data?.imported ?? 0))
    .replace("{deduplicated}", String(parsed.data?.deduplicated ?? 0))
    .replace("{invalid}", String(parsed.data?.invalid ?? 0));
  await load();
}

function askDelete(row: CrowdResponse): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteCrowd(row.id as number);
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="crowd-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.crowd.title }}</h2>
      <a-button type="primary" v-auth="PERMS.TASK_CROWD_CREATE" data-testid="crowd-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-button type="primary" data-testid="crowd-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="crowd-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.TASK_CROWD_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.crowd.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.crowd.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.crowd.itemCount">
        <template #default="{ record: row }">{{ row.itemCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.TASK_CROWD_UPDATE" data-testid="crowd-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" v-auth="PERMS.TASK_CROWD_UPDATE" data-testid="crowd-import" @click="openImport(row)">
              {{ zhCN.crowd.import }}
            </a-button>
            <a-button size="small" danger v-auth="PERMS.TASK_CROWD_DELETE" data-testid="crowd-delete" @click="askDelete(row)">
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
      <a-form-item :label="zhCN.crowd.code">
        <a-input v-model:value="form.code" data-testid="crowd-code" :disabled="editing != null" required />
      </a-form-item>
      <a-form-item :label="zhCN.crowd.name">
        <a-input v-model:value="form.name" data-testid="crowd-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.common.status">
        <a-select v-model:value="form.status">
        <a-select-option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</a-select-option>
        <a-select-option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</a-select-option>
      </a-select>
      </a-form-item>
    </FormDialog>
    <FormDialog
      :visible="importOpen"
      :title="zhCN.crowd.import"
      :saving="saving"
      @submit="submitImport"
      @cancel="importOpen = false"
    >
      <a-form-item :label="zhCN.crowd.content">
        <a-textarea v-model:value="importContent" data-testid="crowd-content" :rows="8" />
      </a-form-item>
      <p v-if="importHint" data-testid="crowd-import-result">{{ importHint }}</p>
    </FormDialog>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>
