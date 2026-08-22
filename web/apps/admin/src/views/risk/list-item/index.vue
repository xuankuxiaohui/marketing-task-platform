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
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "RiskListItemPage" });

const records = ref<RiskListItemResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
  const parsed = okOrFeedback(result);
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
  const parsed = okOrFeedback(result);
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
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  removeOpen.value = false;
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="list-item-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.list.title }}</h2>
      <el-button type="primary" v-if="canAdd" data-testid="list-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-select v-model="filters.dimension" data-testid="filter-dimension">
        <el-option value="" :label="zhCN.list.dimension" />
        <el-option v-for="item in RISK_DIMENSIONS" :key="item" :value="item" :label="item" />
      </el-select>
      <el-select v-model="filters.listType" data-testid="filter-list-type">
        <el-option value="" :label="zhCN.list.listType" />
        <el-option v-for="item in RISK_LIST_TYPES" :key="item" :value="item" :label="item" />
      </el-select>
      <el-input v-model="filters.value" data-testid="filter-value" :placeholder="zhCN.list.listValue" />
      <el-input v-model="filters.from" data-testid="filter-from" type="datetime-local" />
      <el-input v-model="filters.to" data-testid="filter-to" type="datetime-local" />
      <el-button data-testid="list-query" @click="load">{{ zhCN.common.query }}</el-button>
      <el-button v-auth="PERMS.RISK_BLACK_IMPORT" data-testid="list-import" @click="openImport">
        {{ zhCN.list.import }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-if="canAdd" text type="primary" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="list-table" size="small" stripe>
      <el-table-column :label="zhCN.list.dimension">
        <template #default="{ row }">{{ row.dimension }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.list.listType">
        <template #default="{ row }">{{ row.listType }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.list.listValue">
        <template #default="{ row }">{{ row.listValue }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.list.reason">
        <template #default="{ row }">{{ row.reason }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.list.denyLogin">
        <template #default="{ row }">{{ row.denyLogin ? zhCN.common.enabled : zhCN.common.disabled }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.list.effectiveAt">
        <template #default="{ row }">{{ formatDateTime(row.effectiveAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.list.expireAt">
        <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text
              v-auth="removePerm(row)"
              data-testid="list-remove"
              @click="openRemove(row)"
            >
              {{ zhCN.list.remove }}
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
      :title="zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <el-form-item :label="zhCN.list.dimension">
        <el-select v-model="form.dimension" data-testid="list-dimension">
        <el-option v-for="item in RISK_DIMENSIONS" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.list.listType">
        <el-select v-model="form.listType" data-testid="list-type">
        <el-option v-if="hasAuth(PERMS.RISK_BLACK_ADD)" value="BLACK" :label="zhCN.list.black" />
        <el-option v-if="hasAuth(PERMS.RISK_WHITE_ADD)" value="WHITE" :label="zhCN.list.white" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.list.listValue">
        <el-input v-model="form.listValue" data-testid="list-value" required />
      </el-form-item>
      <el-form-item :label="zhCN.list.reason">
        <el-input v-model="form.reason" data-testid="list-reason" required />
      </el-form-item>
      <el-form-item :label="zhCN.list.expireAt">
        <el-input v-model="form.expireAt" data-testid="list-expire" type="datetime-local" />
      </el-form-item>
      <el-form-item v-if="showDenyLogin" :label="zhCN.list.denyLogin">
        <el-checkbox v-model="form.denyLogin" data-testid="list-deny-login" />
      </el-form-item>
      <el-form-item :label="zhCN.common.remark">
        <el-input v-model="form.remark" data-testid="list-remark" />
      </el-form-item>
    </FormDialog>
    <FormDialog
      :visible="importOpen"
      :title="zhCN.list.import"
      :saving="saving"
      @submit="submitImport"
      @cancel="importOpen = false"
    >
      <el-form-item :label="zhCN.list.dimension">
        <el-select v-model="form.dimension" data-testid="import-dimension">
        <el-option v-for="item in RISK_DIMENSIONS" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.list.content">
        <el-input v-model="form.listValue" type="textarea" data-testid="import-content" :rows="8" required  />
      </el-form-item>
      <el-form-item :label="zhCN.list.reason">
        <el-input v-model="form.reason" data-testid="import-reason" required />
      </el-form-item>
      <p v-if="importHint" data-testid="import-result">{{ importHint }}</p>
    </FormDialog>
    <FormDialog
      :visible="removeOpen"
      :title="zhCN.list.remove"
      :saving="saving"
      @submit="submitRemove"
      @cancel="removeOpen = false"
    >
      <el-form-item :label="zhCN.list.removeReason">
        <el-input v-model="removeReason" data-testid="remove-reason" required />
      </el-form-item>
    </FormDialog>
  </section>
</template>
