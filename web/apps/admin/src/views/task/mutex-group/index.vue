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
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "TaskMutexGroupPage" });

const records = ref<MutexGroupResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
  const parsed = okOrFeedback(result);
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
  <section class="admin-page" data-testid="mutex-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.mutex.title }}</h2>
      <el-button type="primary" v-auth="PERMS.TASK_MUTEX_CREATE" data-testid="mutex-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-button data-testid="mutex-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.TASK_MUTEX_CREATE" text type="primary" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="mutex-table" size="small" stripe>
      <el-table-column :label="zhCN.mutex.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.mutex.name">
        <template #default="{ row }">{{ row.name }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.mutex.crossCycle">
        <template #default="{ row }">{{ row.crossCycle ? "Y" : "N" }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.TASK_MUTEX_UPDATE" data-testid="mutex-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button text v-auth="PERMS.TASK_MUTEX_DELETE" data-testid="mutex-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.prevPage }}</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.nextPage }}</el-button>
    </div>
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <el-form-item :label="zhCN.mutex.code">
        <el-input v-model="form.code" data-testid="mutex-code" :disabled="editing != null" required />
      </el-form-item>
      <el-form-item :label="zhCN.mutex.name">
        <el-input v-model="form.name" data-testid="mutex-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.mutex.crossCycle">
        <el-checkbox v-model="form.crossCycle" data-testid="mutex-cross" />
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
