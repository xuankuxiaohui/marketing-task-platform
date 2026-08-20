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
    <div class="admin-toolbar">
      <button type="button" data-testid="crowd-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.TASK_CROWD_CREATE" type="button" data-testid="crowd-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="crowd-table">
      <thead>
        <tr>
          <th>{{ zhCN.crowd.code }}</th>
          <th>{{ zhCN.crowd.name }}</th>
          <th>{{ zhCN.crowd.itemCount }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.itemCount }}</td>
          <td>{{ row.status }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.TASK_CROWD_UPDATE" type="button" data-testid="crowd-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button v-auth="PERMS.TASK_CROWD_UPDATE" type="button" data-testid="crowd-import" @click="openImport(row)">
              {{ zhCN.crowd.import }}
            </button>
            <button v-auth="PERMS.TASK_CROWD_DELETE" type="button" data-testid="crowd-delete" @click="askDelete(row)">
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
        <span>{{ zhCN.crowd.code }}</span>
        <input v-model="form.code" data-testid="crowd-code" :disabled="editing != null" required />
      </label>
      <label class="field">
        <span>{{ zhCN.crowd.name }}</span>
        <input v-model="form.name" data-testid="crowd-name" required />
      </label>
      <label class="field">
        <span>{{ zhCN.common.status }}</span>
        <select v-model="form.status">
          <option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</option>
          <option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</option>
        </select>
      </label>
    </FormDialog>
    <FormDialog
      :visible="importOpen"
      :title="zhCN.crowd.import"
      :saving="saving"
      @submit="submitImport"
      @cancel="importOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.crowd.content }}</span>
        <textarea v-model="importContent" data-testid="crowd-content" rows="8" />
      </label>
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
