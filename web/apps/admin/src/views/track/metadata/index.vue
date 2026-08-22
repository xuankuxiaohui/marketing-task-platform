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
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "TrackMetadataPage" });

const records = ref<TrackMetadataResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
  const parsed = okOrFeedback(result);
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
  <section class="admin-page" data-testid="metadata-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.metadata.title }}</h2>
      <el-button type="primary" v-auth="PERMS.TRACK_META_CREATE" data-testid="metadata-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.eventCode" data-testid="filter-code" :placeholder="zhCN.metadata.eventCode" />
      <el-select v-model="filters.status" data-testid="filter-status">
        <el-option value="" :label="zhCN.common.status" />
        <el-option :value="STATUS.ENABLED" :label="zhCN.common.enabled" />
        <el-option :value="STATUS.DISABLED" :label="zhCN.common.disabled" />
      </el-select>
      <el-button v-auth="PERMS.TRACK_META_QUERY" data-testid="metadata-query" @click="load">
        {{ zhCN.common.query }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.TRACK_META_CREATE" text type="primary" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="metadata-table" size="small" stripe>
      <el-table-column :label="zhCN.metadata.eventCode">
        <template #default="{ row }">{{ row.eventCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.metadata.name">
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
      <el-table-column :label="zhCN.metadata.owner">
        <template #default="{ row }">{{ row.owner }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.TRACK_META_UPDATE" data-testid="metadata-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button text v-auth="PERMS.TRACK_META_DELETE" data-testid="metadata-delete" @click="askDelete(row)">
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
      <el-form-item :label="zhCN.metadata.eventCode">
        <el-input v-model="form.eventCode" data-testid="metadata-code" :disabled="editing != null" required />
      </el-form-item>
      <p v-if="editing" class="hint">{{ zhCN.metadata.eventCodeLocked }}</p>
      <el-form-item :label="zhCN.metadata.name">
        <el-input v-model="form.name" data-testid="metadata-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.metadata.propSchema">
        <el-input v-model="form.propSchema" type="textarea" data-testid="metadata-schema" :rows="6"  />
      </el-form-item>
      <el-form-item :label="zhCN.common.status">
        <el-select v-model="form.status" data-testid="metadata-status">
        <el-option :value="STATUS.ENABLED" :label="zhCN.common.enabled" />
        <el-option :value="STATUS.DISABLED" :label="zhCN.common.disabled" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.metadata.owner">
        <el-input v-model="form.owner" data-testid="metadata-owner" />
      </el-form-item>
      <el-form-item :label="zhCN.common.remark">
        <el-input v-model="form.remark" />
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
