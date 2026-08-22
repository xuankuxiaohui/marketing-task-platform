<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  createDictEntry,
  createDictType,
  deleteDictType,
  listDictEntries,
  pageDictTypes,
  updateDictType,
  type DictEntryOption,
  type DictTypeView,
} from "@/api/system";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "DictManagePage" });

const types = ref<DictTypeView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const selected = ref<DictTypeView | null>(null);
const entries = ref<DictEntryOption[]>([]);
const formOpen = ref(false);
const entryOpen = ref(false);
const saving = ref(false);
const editing = ref<DictTypeView | null>(null);
const form = reactive({ code: "", name: "", remark: "", status: STATUS.ENABLED as string });
const entryForm = reactive({ label: "", value: "", sort: 0, remark: "" });
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageDictTypes({ page: page.value, pageSize });
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  types.value = parsed.data?.records ?? [];
  total.value = parsed.data?.total ?? 0;
}

async function selectType(row: DictTypeView): Promise<void> {
  selected.value = row;
  if (!row.code) {
    entries.value = [];
    return;
  }
  const result = await listDictEntries(row.code);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  entries.value = parsed.data ?? [];
}

function openCreate(): void {
  editing.value = null;
  form.code = "";
  form.name = "";
  form.remark = "";
  form.status = STATUS.ENABLED;
  formOpen.value = true;
}

function openEdit(row: DictTypeView): void {
  editing.value = row;
  form.code = row.code ?? "";
  form.name = row.name ?? "";
  form.remark = row.remark ?? "";
  form.status = row.status ?? STATUS.ENABLED;
  formOpen.value = true;
}

async function submitType(): Promise<void> {
  saving.value = true;
  const result: Result = editing.value?.id
    ? await updateDictType(editing.value.id, { name: form.name, status: form.status, remark: form.remark })
    : await createDictType({ code: form.code, name: form.name, remark: form.remark });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function submitEntry(): Promise<void> {
  if (!selected.value?.code) {
    return;
  }
  saving.value = true;
  const result = await createDictEntry({
    typeCode: selected.value.code,
    label: entryForm.label,
    value: entryForm.value,
    sort: Number(entryForm.sort),
    remark: entryForm.remark || undefined,
  });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  entryOpen.value = false;
  await selectType(selected.value);
}

function askDelete(row: DictTypeView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteDictType(row.id as number);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      if (selected.value?.id === row.id) {
        selected.value = null;
        entries.value = [];
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
  <section class="admin-page" data-testid="dict-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.dict.title }}</h2>
      <el-button type="primary" v-auth="PERMS.DICT_TYPE_CREATE" data-testid="dict-type-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="types.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.DICT_TYPE_CREATE" text type="primary" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="types" class="data-table admin-table" data-testid="dict-type-table" size="small" stripe>
      <el-table-column :label="zhCN.dict.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.dict.name">
        <template #default="{ row }">{{ row.name }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'info'"
            :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'"
          >
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="zhCN.common.createdAt">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text data-testid="dict-type-select" @click="selectType(row)">{{ zhCN.dict.entries }}</el-button>
            <el-button text v-auth="PERMS.DICT_TYPE_UPDATE" data-testid="dict-type-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button text v-auth="PERMS.DICT_TYPE_DELETE" data-testid="dict-type-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="selected" class="entry-panel" data-testid="dict-entry-panel">
      <h3>{{ zhCN.dict.entries }} · {{ selected.code }}</h3>
      <p class="hint">{{ zhCN.dict.enabledOnly }}</p>
      <el-button
        v-auth="PERMS.DICT_ENTRY_CREATE"
        data-testid="dict-entry-create"
        @click="entryOpen = true"
      >
        {{ zhCN.dict.addEntry }}
      </el-button>
      <el-table :data="entries" class="data-table admin-table" data-testid="dict-entry-table" size="small" stripe>
      <el-table-column :label="zhCN.dict.label">
        <template #default="{ row }">{{ row.label }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.dict.value">
        <template #default="{ row }">{{ row.value }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.dict.sort">
        <template #default="{ row }">{{ row.sort }}</template>
      </el-table-column>
    </el-table>
    </div>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</el-button>
    </div>
    <FormDialog :visible="formOpen" :title="editing ? zhCN.common.edit : zhCN.common.create" :saving="saving" @submit="submitType" @cancel="formOpen = false">
      <el-form-item v-if="!editing" :label="zhCN.dict.code">
        <el-input v-model="form.code" data-testid="dict-code" required />
      </el-form-item>
      <el-form-item :label="zhCN.dict.name">
        <el-input v-model="form.name" data-testid="dict-name" required />
      </el-form-item>
      <el-form-item v-if="editing" :label="zhCN.common.status">
        <el-select v-model="form.status">
        <el-option :value="STATUS.ENABLED" :label="zhCN.common.enabled" />
        <el-option :value="STATUS.DISABLED" :label="zhCN.common.disabled" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.common.remark">
        <el-input v-model="form.remark" />
      </el-form-item>
    </FormDialog>
    <FormDialog :visible="entryOpen" :title="zhCN.dict.addEntry" :saving="saving" @submit="submitEntry" @cancel="entryOpen = false">
      <el-form-item :label="zhCN.dict.label">
        <el-input v-model="entryForm.label" data-testid="entry-label" required />
      </el-form-item>
      <el-form-item :label="zhCN.dict.value">
        <el-input v-model="entryForm.value" data-testid="entry-value" required />
      </el-form-item>
      <el-form-item :label="zhCN.dict.sort">
        <el-input v-model.number="entryForm.sort" data-testid="entry-sort" type="number" required />
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
