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
    <h2>{{ zhCN.metadata.title }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.eventCode" data-testid="filter-code" :placeholder="zhCN.metadata.eventCode" />
      <select v-model="filters.status" data-testid="filter-status">
        <option value="">{{ zhCN.common.status }}</option>
        <option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</option>
        <option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</option>
      </select>
      <button v-auth="PERMS.TRACK_META_QUERY" type="button" data-testid="metadata-query" @click="load">
        {{ zhCN.common.query }}
      </button>
      <button v-auth="PERMS.TRACK_META_CREATE" type="button" data-testid="metadata-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="metadata-table">
      <thead>
        <tr>
          <th>{{ zhCN.metadata.eventCode }}</th>
          <th>{{ zhCN.metadata.name }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.metadata.owner }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.eventCode }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.status }}</td>
          <td>{{ row.owner }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.TRACK_META_UPDATE" type="button" data-testid="metadata-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button v-auth="PERMS.TRACK_META_DELETE" type="button" data-testid="metadata-delete" @click="askDelete(row)">
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
        <span>{{ zhCN.metadata.eventCode }}</span>
        <input v-model="form.eventCode" data-testid="metadata-code" :disabled="editing != null" required />
      </label>
      <p v-if="editing" class="hint">{{ zhCN.metadata.eventCodeLocked }}</p>
      <label class="field">
        <span>{{ zhCN.metadata.name }}</span>
        <input v-model="form.name" data-testid="metadata-name" required />
      </label>
      <label class="field">
        <span>{{ zhCN.metadata.propSchema }}</span>
        <textarea v-model="form.propSchema" data-testid="metadata-schema" rows="6" />
      </label>
      <label class="field">
        <span>{{ zhCN.common.status }}</span>
        <select v-model="form.status" data-testid="metadata-status">
          <option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</option>
          <option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.metadata.owner }}</span>
        <input v-model="form.owner" data-testid="metadata-owner" />
      </label>
      <label class="field">
        <span>{{ zhCN.common.remark }}</span>
        <input v-model="form.remark" />
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
