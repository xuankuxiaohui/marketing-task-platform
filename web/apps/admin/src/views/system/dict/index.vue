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
import { adminStatusLabel } from "@/utils/status-label";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "DictManagePage" });

const types = ref<DictTypeView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="dict-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.dict.title }}</h2>
      <a-button type="primary" v-auth="PERMS.DICT_TYPE_CREATE" data-testid="dict-type-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="types" class="data-table admin-table" data-testid="dict-type-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.DICT_TYPE_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.dict.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.dict.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" data-testid="dict-type-select" @click="selectType(row)">{{ zhCN.dict.entries }}</a-button>
            <a-button size="small" v-auth="PERMS.DICT_TYPE_UPDATE" data-testid="dict-type-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" danger v-auth="PERMS.DICT_TYPE_DELETE" data-testid="dict-type-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <div v-if="selected" class="entry-panel" data-testid="dict-entry-panel">
      <h3>{{ zhCN.dict.entries }} · {{ selected.code }}</h3>
      <p class="hint">{{ zhCN.dict.enabledOnly }}</p>
      <a-button v-auth="PERMS.DICT_ENTRY_CREATE" data-testid="dict-entry-create" @click="entryOpen = true">
        {{ zhCN.dict.addEntry }}
      </a-button>
      <a-table :data-source="entries" class="data-table admin-table" data-testid="dict-entry-table" size="small" :pagination="false" :row-key="adminRowKey">
      <a-table-column :title="zhCN.dict.label">
        <template #default="{ record: row }">{{ row.label }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.dict.value">
        <template #default="{ record: row }">{{ row.value }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.dict.sort">
        <template #default="{ record: row }">{{ row.sort }}</template>
      </a-table-column>
    </a-table>
    </div>
    <FormDialog :visible="formOpen" :title="editing ? zhCN.common.edit : zhCN.common.create" :saving="saving" @submit="submitType" @cancel="formOpen = false">
      <a-form-item v-if="!editing" :label="zhCN.dict.code">
        <a-input v-model:value="form.code" data-testid="dict-code" required />
      </a-form-item>
      <a-form-item :label="zhCN.dict.name">
        <a-input v-model:value="form.name" data-testid="dict-name" required />
      </a-form-item>
      <a-form-item v-if="editing" :label="zhCN.common.status">
        <a-select v-model:value="form.status">
        <a-select-option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</a-select-option>
        <a-select-option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.common.remark">
        <a-input v-model:value="form.remark" />
      </a-form-item>
    </FormDialog>
    <FormDialog :visible="entryOpen" :title="zhCN.dict.addEntry" :saving="saving" @submit="submitEntry" @cancel="entryOpen = false">
      <a-form-item :label="zhCN.dict.label">
        <a-input v-model:value="entryForm.label" data-testid="entry-label" required />
      </a-form-item>
      <a-form-item :label="zhCN.dict.value">
        <a-input v-model:value="entryForm.value" data-testid="entry-value" required />
      </a-form-item>
      <a-form-item :label="zhCN.dict.sort">
        <a-input v-model:value.number="entryForm.sort" data-testid="entry-sort" required type="number" />
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
