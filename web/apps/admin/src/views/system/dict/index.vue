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
    <h2>{{ zhCN.dict.title }}</h2>
    <div class="admin-toolbar">
      <button v-auth="PERMS.DICT_TYPE_CREATE" type="button" data-testid="dict-type-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="types.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="dict-type-table">
      <thead>
        <tr>
          <th>{{ zhCN.dict.code }}</th>
          <th>{{ zhCN.dict.name }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in types" :key="row.id" :class="{ selected: selected?.id === row.id }">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.status }}</td>
          <td>{{ formatDateTime(row.createdAt) }}</td>
          <td class="row-actions">
            <button type="button" data-testid="dict-type-select" @click="selectType(row)">{{ zhCN.dict.entries }}</button>
            <button v-auth="PERMS.DICT_TYPE_UPDATE" type="button" data-testid="dict-type-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button v-auth="PERMS.DICT_TYPE_DELETE" type="button" data-testid="dict-type-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="selected" class="entry-panel" data-testid="dict-entry-panel">
      <h3>{{ zhCN.dict.entries }} · {{ selected.code }}</h3>
      <p class="hint">{{ zhCN.dict.enabledOnly }}</p>
      <button
        v-auth="PERMS.DICT_ENTRY_CREATE"
        type="button"
        data-testid="dict-entry-create"
        @click="entryOpen = true"
      >
        {{ zhCN.dict.addEntry }}
      </button>
      <table class="data-table" data-testid="dict-entry-table">
        <thead>
          <tr>
            <th>{{ zhCN.dict.label }}</th>
            <th>{{ zhCN.dict.value }}</th>
            <th>{{ zhCN.dict.sort }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in entries" :key="`${item.value}-${item.sort}`">
            <td>{{ item.label }}</td>
            <td>{{ item.value }}</td>
            <td>{{ item.sort }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <button type="button" :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</button>
      <span>{{ page }}</span>
      <button type="button" :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</button>
    </div>
    <FormDialog :visible="formOpen" :title="editing ? zhCN.common.edit : zhCN.common.create" :saving="saving" @submit="submitType" @cancel="formOpen = false">
      <label v-if="!editing" class="field">
        <span>{{ zhCN.dict.code }}</span>
        <input v-model="form.code" data-testid="dict-code" required />
      </label>
      <label class="field">
        <span>{{ zhCN.dict.name }}</span>
        <input v-model="form.name" data-testid="dict-name" required />
      </label>
      <label v-if="editing" class="field">
        <span>{{ zhCN.common.status }}</span>
        <select v-model="form.status">
          <option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</option>
          <option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.common.remark }}</span>
        <input v-model="form.remark" />
      </label>
    </FormDialog>
    <FormDialog :visible="entryOpen" :title="zhCN.dict.addEntry" :saving="saving" @submit="submitEntry" @cancel="entryOpen = false">
      <label class="field">
        <span>{{ zhCN.dict.label }}</span>
        <input v-model="entryForm.label" data-testid="entry-label" required />
      </label>
      <label class="field">
        <span>{{ zhCN.dict.value }}</span>
        <input v-model="entryForm.value" data-testid="entry-value" required />
      </label>
      <label class="field">
        <span>{{ zhCN.dict.sort }}</span>
        <input v-model.number="entryForm.sort" data-testid="entry-sort" type="number" required />
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
