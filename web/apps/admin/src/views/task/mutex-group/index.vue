<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  createMutexGroup,
  deleteMutexGroup,
  pageMutexGroups,
  updateMutexGroup,
  type MutexGroupResponse,
} from "@/api/task";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "TaskMutexGroupPage" });

const records = ref<MutexGroupResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<MutexGroupResponse | null>(null);
const form = reactive({ code: "", name: "", crossCycle: false });
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageMutexGroups({ page: page.value, pageSize });
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
  form.crossCycle = false;
  formOpen.value = true;
}

function openEdit(row: MutexGroupResponse): void {
  editing.value = row;
  form.code = row.code ?? "";
  form.name = row.name ?? "";
  form.crossCycle = Boolean(row.crossCycle);
  formOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const body = { code: form.code, name: form.name, crossCycle: form.crossCycle };
  const result: Result = editing.value?.id != null ? await updateMutexGroup(editing.value.id, body) : await createMutexGroup(body);
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

function askDelete(row: MutexGroupResponse): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteMutexGroup(row.id as number);
      const parsed = writeOrFeedback(result);
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
  <section class="admin-page" data-testid="mutex-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.mutex.title }}</h2>
      <a-button type="primary" v-auth="PERMS.TASK_MUTEX_CREATE" data-testid="mutex-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-button type="primary" data-testid="mutex-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="mutex-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.TASK_MUTEX_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.mutex.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.mutex.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.mutex.crossCycle">
        <template #default="{ record: row }">{{ row.crossCycle ? "Y" : "N" }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.TASK_MUTEX_UPDATE" data-testid="mutex-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" danger v-auth="PERMS.TASK_MUTEX_DELETE" data-testid="mutex-delete" @click="askDelete(row)">
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
      <a-form-item :label="zhCN.mutex.code">
        <a-input v-model:value="form.code" data-testid="mutex-code" :disabled="editing != null" required />
      </a-form-item>
      <a-form-item :label="zhCN.mutex.name">
        <a-input v-model:value="form.name" data-testid="mutex-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.mutex.crossCycle">
        <a-checkbox v-model:checked="form.crossCycle" data-testid="mutex-cross" />
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
