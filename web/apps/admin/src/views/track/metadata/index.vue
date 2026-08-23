<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  createMetadata,
  deleteMetadata,
  pageMetadata,
  updateMetadata,
  type TrackMetadataResponse,
} from "@/api/track";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "TrackMetadataPage" });

const records = ref<TrackMetadataResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ eventCode: "", status: "" });
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<TrackMetadataResponse | null>(null);
const form = reactive({
  eventCode: "",
  name: "",
  propSchema: "[]",
  status: STATUS.ENABLED as string,
  owner: "",
  remark: "",
});
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageMetadata({
    eventCode: filters.eventCode,
    status: filters.status,
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

function parseSchema() {
  if (!form.propSchema.trim()) {
    return undefined;
  }
  try {
    return JSON.parse(form.propSchema) as TrackMetadataResponse["propSchema"];
  } catch {
    return undefined;
  }
}

function buildBody() {
  return {
    eventCode: form.eventCode,
    name: form.name,
    propSchema: parseSchema(),
    status: form.status,
    owner: form.owner || undefined,
    remark: form.remark || undefined,
  };
}

function openCreate(): void {
  editing.value = null;
  form.eventCode = "";
  form.name = "";
  form.propSchema = "[]";
  form.status = STATUS.ENABLED;
  form.owner = "";
  form.remark = "";
  formOpen.value = true;
}

function openEdit(row: TrackMetadataResponse): void {
  editing.value = row;
  form.eventCode = row.eventCode ?? "";
  form.name = row.name ?? "";
  form.propSchema = JSON.stringify(row.propSchema ?? []);
  form.status = row.status ?? STATUS.ENABLED;
  form.owner = row.owner ?? "";
  form.remark = row.remark ?? "";
  formOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result: Result =
    editing.value?.id != null ? await updateMetadata(editing.value.id, buildBody()) : await createMetadata(buildBody());
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

function askDelete(row: TrackMetadataResponse): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteMetadata(row.id as number);
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
  <section class="admin-page" data-testid="metadata-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.metadata.title }}</h2>
      <a-button type="primary" v-auth="PERMS.TRACK_META_CREATE" data-testid="metadata-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.eventCode" data-testid="filter-code" :placeholder="zhCN.metadata.eventCode" />
      <a-select v-model:value="filters.status" data-testid="filter-status">
        <a-select-option value="">{{ zhCN.common.status }}</a-select-option>
        <a-select-option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</a-select-option>
        <a-select-option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</a-select-option>
      </a-select>
      <a-button type="primary" v-auth="PERMS.TRACK_META_QUERY" data-testid="metadata-query" @click="load">
        {{ zhCN.common.query }}
      </a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="metadata-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.TRACK_META_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.metadata.eventCode">
        <template #default="{ record: row }">{{ row.eventCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.metadata.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.metadata.owner">
        <template #default="{ record: row }">{{ row.owner }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.TRACK_META_UPDATE" data-testid="metadata-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" danger v-auth="PERMS.TRACK_META_DELETE" data-testid="metadata-delete" @click="askDelete(row)">
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
      <a-form-item :label="zhCN.metadata.eventCode">
        <a-input v-model:value="form.eventCode" data-testid="metadata-code" :disabled="editing != null" required />
      </a-form-item>
      <p v-if="editing" class="hint">{{ zhCN.metadata.eventCodeLocked }}</p>
      <a-form-item :label="zhCN.metadata.name">
        <a-input v-model:value="form.name" data-testid="metadata-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.metadata.propSchema">
        <a-textarea v-model:value="form.propSchema" data-testid="metadata-schema" :rows="6" />
      </a-form-item>
      <a-form-item :label="zhCN.common.status">
        <a-select v-model:value="form.status" data-testid="metadata-status">
        <a-select-option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</a-select-option>
        <a-select-option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.metadata.owner">
        <a-input v-model:value="form.owner" data-testid="metadata-owner" />
      </a-form-item>
      <a-form-item :label="zhCN.common.remark">
        <a-input v-model:value="form.remark" />
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
