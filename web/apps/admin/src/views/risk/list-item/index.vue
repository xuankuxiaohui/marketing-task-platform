<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  addListItem,
  importListItems,
  pageListItems,
  removeListItem,
  type RiskListItemResponse,
} from "@/api/risk";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { RISK_DIMENSIONS, RISK_LIST_TYPES } from "@/constants/risk";
import { hasAuth } from "@/directives/auth";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime, toIsoInstant } from "@/utils/datetime";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "RiskListItemPage" });

const records = ref<RiskListItemResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ dimension: "", listType: "", value: "", from: "", to: "" });
const formOpen = ref(false);
const importOpen = ref(false);
const removeOpen = ref(false);
const saving = ref(false);
const importHint = ref("");
const removing = ref<RiskListItemResponse | null>(null);
const removeReason = ref("");
const form = reactive({
  dimension: "USER" as (typeof RISK_DIMENSIONS)[number],
  listType: "BLACK" as (typeof RISK_LIST_TYPES)[number],
  listValue: "",
  reason: "",
  expireAt: "",
  denyLogin: false,
  remark: "",
});

const canAdd = computed(() => hasAuth(PERMS.RISK_BLACK_ADD) || hasAuth(PERMS.RISK_WHITE_ADD));
const showDenyLogin = computed(() => form.dimension === "USER" && form.listType === "BLACK");

function removePerm(row: RiskListItemResponse): string {
  return row.listType === "WHITE" ? PERMS.RISK_WHITE_REMOVE : PERMS.RISK_BLACK_REMOVE;
}

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageListItems({
    dimension: (filters.dimension || undefined) as "USER" | "IP" | "DEVICE" | undefined,
    listType: (filters.listType || undefined) as "BLACK" | "WHITE" | undefined,
    value: filters.value,
    from: toIsoInstant(filters.from),
    to: toIsoInstant(filters.to),
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

function openCreate(): void {
  form.dimension = "USER";
  form.listType = hasAuth(PERMS.RISK_BLACK_ADD) ? "BLACK" : "WHITE";
  form.listValue = "";
  form.reason = "";
  form.expireAt = "";
  form.denyLogin = false;
  form.remark = "";
  formOpen.value = true;
}

function openImport(): void {
  form.dimension = "USER";
  form.listValue = "";
  form.reason = "";
  importHint.value = "";
  importOpen.value = true;
}

function openRemove(row: RiskListItemResponse): void {
  removing.value = row;
  removeReason.value = "";
  removeOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result = await addListItem({
    dimension: form.dimension,
    listType: form.listType,
    listValue: form.listValue,
    reason: form.reason,
    expireAt: toIsoInstant(form.expireAt),
    denyLogin: showDenyLogin.value ? form.denyLogin : undefined,
    remark: form.remark || undefined,
  });
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function submitImport(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result = await importListItems({
    dimension: form.dimension,
    listType: "BLACK",
    content: form.listValue,
    reason: form.reason,
  });
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  importHint.value = zhCN.list.importResult
    .replace("{imported}", String(parsed.data?.imported ?? 0))
    .replace("{invalid}", String(parsed.data?.invalid ?? 0));
  await load();
}

async function submitRemove(): Promise<void> {
  if (removing.value?.id == null) {
    return;
  }
  saving.value = true;
  feedback.value = null;
  const result = await removeListItem(removing.value.id, { reason: removeReason.value });
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  removeOpen.value = false;
  await load();
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
  <section class="admin-page" data-testid="list-item-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.list.title }}</h2>
      <a-button type="primary" v-if="canAdd" data-testid="list-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-select v-model:value="filters.dimension" data-testid="filter-dimension">
        <a-select-option value="">{{ zhCN.list.dimension }}</a-select-option>
        <a-select-option v-for="item in RISK_DIMENSIONS" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      <a-select v-model:value="filters.listType" data-testid="filter-list-type">
        <a-select-option value="">{{ zhCN.list.listType }}</a-select-option>
        <a-select-option v-for="item in RISK_LIST_TYPES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      <a-input v-model:value="filters.value" data-testid="filter-value" :placeholder="zhCN.list.listValue" />
      <a-date-picker v-model:value="filters.from" data-testid="filter-from" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-date-picker v-model:value="filters.to" data-testid="filter-to" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-button type="primary" data-testid="list-query" @click="load">{{ zhCN.common.query }}</a-button>
      <a-button v-auth="PERMS.RISK_BLACK_IMPORT" data-testid="list-import" @click="openImport">
        {{ zhCN.list.import }}
      </a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="list-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-if="canAdd" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.list.dimension">
        <template #default="{ record: row }">{{ row.dimension }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.list.listType">
        <template #default="{ record: row }">{{ row.listType }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.list.listValue">
        <template #default="{ record: row }">{{ row.listValue }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.list.reason">
        <template #default="{ record: row }">{{ row.reason }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.list.denyLogin">
        <template #default="{ record: row }">{{ row.denyLogin ? zhCN.common.enabled : zhCN.common.disabled }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.list.effectiveAt">
        <template #default="{ record: row }">{{ formatDateTime(row.effectiveAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.list.expireAt">
        <template #default="{ record: row }">{{ formatDateTime(row.expireAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="removePerm(row)" data-testid="list-remove" @click="openRemove(row)">
              {{ zhCN.list.remove }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog
      :visible="formOpen"
      :title="zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <a-form-item :label="zhCN.list.dimension">
        <a-select v-model:value="form.dimension" data-testid="list-dimension">
        <a-select-option v-for="item in RISK_DIMENSIONS" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.list.listType">
        <a-select v-model:value="form.listType" data-testid="list-type">
        <a-select-option v-if="hasAuth(PERMS.RISK_BLACK_ADD)" value="BLACK">{{ zhCN.list.black }}</a-select-option>
        <a-select-option v-if="hasAuth(PERMS.RISK_WHITE_ADD)" value="WHITE">{{ zhCN.list.white }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.list.listValue">
        <a-input v-model:value="form.listValue" data-testid="list-value" required />
      </a-form-item>
      <a-form-item :label="zhCN.list.reason">
        <a-input v-model:value="form.reason" data-testid="list-reason" required />
      </a-form-item>
      <a-form-item :label="zhCN.list.expireAt">
        <a-date-picker v-model:value="form.expireAt" data-testid="list-expire" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
      <a-form-item v-if="showDenyLogin" :label="zhCN.list.denyLogin">
        <a-checkbox v-model:checked="form.denyLogin" data-testid="list-deny-login" />
      </a-form-item>
      <a-form-item :label="zhCN.common.remark">
        <a-input v-model:value="form.remark" data-testid="list-remark" />
      </a-form-item>
    </FormDialog>
    <FormDialog
      :visible="importOpen"
      :title="zhCN.list.import"
      :saving="saving"
      @submit="submitImport"
      @cancel="importOpen = false"
    >
      <a-form-item :label="zhCN.list.dimension">
        <a-select v-model:value="form.dimension" data-testid="import-dimension">
        <a-select-option v-for="item in RISK_DIMENSIONS" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.list.content">
        <a-textarea v-model:value="form.listValue" data-testid="import-content" :rows="8" required />
      </a-form-item>
      <a-form-item :label="zhCN.list.reason">
        <a-input v-model:value="form.reason" data-testid="import-reason" required />
      </a-form-item>
      <p v-if="importHint" data-testid="import-result">{{ importHint }}</p>
    </FormDialog>
    <FormDialog
      :visible="removeOpen"
      :title="zhCN.list.remove"
      :saving="saving"
      @submit="submitRemove"
      @cancel="removeOpen = false"
    >
      <a-form-item :label="zhCN.list.removeReason">
        <a-input v-model:value="removeReason" data-testid="remove-reason" required />
      </a-form-item>
    </FormDialog>
  </section>
</template>
