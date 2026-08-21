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
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "TaskCrowdPage" });

const records = ref<CrowdResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="crowd-page">
    <h2>{{ zhCN.crowd.title }}</h2>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-button data-testid="crowd-query" @click="load">{{ zhCN.common.query }}</el-button>
      <el-button v-auth="PERMS.TASK_CROWD_CREATE" data-testid="crowd-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="records" class="data-table" data-testid="crowd-table" stripe>
      <el-table-column :label="zhCN.crowd.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.crowd.name">
        <template #default="{ row }">{{ row.name }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.crowd.itemCount">
        <template #default="{ row }">{{ row.itemCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-auth="PERMS.TASK_CROWD_UPDATE" data-testid="crowd-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button v-auth="PERMS.TASK_CROWD_UPDATE" data-testid="crowd-import" @click="openImport(row)">
              {{ zhCN.crowd.import }}
            </el-button>
            <el-button v-auth="PERMS.TASK_CROWD_DELETE" data-testid="crowd-delete" @click="askDelete(row)">
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
      <el-form-item :label="zhCN.crowd.code">
        <el-input v-model="form.code" data-testid="crowd-code" :disabled="editing != null" required />
      </el-form-item>
      <el-form-item :label="zhCN.crowd.name">
        <el-input v-model="form.name" data-testid="crowd-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.common.status">
        <el-select v-model="form.status">
        <el-option :value="STATUS.ENABLED" :label="zhCN.common.enabled" />
        <el-option :value="STATUS.DISABLED" :label="zhCN.common.disabled" />
      </el-select>
      </el-form-item>
    </FormDialog>
    <FormDialog
      :visible="importOpen"
      :title="zhCN.crowd.import"
      :saving="saving"
      @submit="submitImport"
      @cancel="importOpen = false"
    >
      <el-form-item :label="zhCN.crowd.content">
        <el-input v-model="importContent" type="textarea" data-testid="crowd-content" :rows="8"  />
      </el-form-item>
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
