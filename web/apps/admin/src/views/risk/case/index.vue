<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { handleCase, pageHits, type RiskHitLogResponse } from "@/api/risk";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { RISK_ACTION_RESULTS, RISK_HANDLE_ACTIONS, RISK_HIT_TYPES, RISK_RULE_CODES } from "@/constants/risk";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime, toIsoInstant } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "RiskCasePage" });

const records = ref<RiskHitLogResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const handleOpen = ref(false);
const saving = ref(false);
const filters = reactive({
  ruleCode: "",
  hitType: "",
  dimensionValue: "",
  userId: "",
  actionResult: "",
  from: "",
  to: "",
});
const form = reactive({
  hitLogId: "",
  userId: "",
  action: "ADD_BLACK" as (typeof RISK_HANDLE_ACTIONS)[number],
  toWhitelist: false,
  reason: "",
  expireAt: "",
});

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageHits({
    ruleCode: filters.ruleCode,
    hitType: filters.hitType,
    dimensionValue: filters.dimensionValue,
    userId: filters.userId ? Number(filters.userId) : undefined,
    actionResult: filters.actionResult,
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

function openHandle(row?: RiskHitLogResponse): void {
  form.hitLogId = row?.id != null ? String(row.id) : "";
  form.userId = row?.userId != null ? String(row.userId) : "";
  form.action = "ADD_BLACK";
  form.toWhitelist = false;
  form.reason = "";
  form.expireAt = "";
  handleOpen.value = true;
}

async function submitHandle(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result = await handleCase({
    hitLogId: form.hitLogId ? Number(form.hitLogId) : undefined,
    userId: form.userId ? Number(form.userId) : undefined,
    action: form.action,
    toWhitelist: form.action === "REMOVE_BLACK" ? form.toWhitelist : false,
    reason: form.reason,
    expireAt: form.action === "ADD_BLACK" ? toIsoInstant(form.expireAt) : undefined,
  });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  handleOpen.value = false;
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
  <section class="admin-page" data-testid="case-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.cases.title }}</h2>
      <a-button type="primary" v-auth="PERMS.RISK_CASE_HANDLE" data-testid="case-handle-open" @click="openHandle()">
        {{ zhCN.cases.handle }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-select v-model:value="filters.ruleCode" data-testid="filter-rule">
        <a-select-option value="">{{ zhCN.cases.ruleCode }}</a-select-option>
        <a-select-option v-for="item in RISK_RULE_CODES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      <a-select v-model:value="filters.hitType" data-testid="filter-hit-type">
        <a-select-option value="">{{ zhCN.cases.hitType }}</a-select-option>
        <a-select-option v-for="item in RISK_HIT_TYPES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      <a-input v-model:value="filters.dimensionValue" data-testid="filter-dimension" :placeholder="zhCN.cases.dimensionValue" />
      <a-input v-model:value="filters.userId" data-testid="filter-user" :placeholder="zhCN.cases.userId" />
      <a-select v-model:value="filters.actionResult" data-testid="filter-action-result">
        <a-select-option value="">{{ zhCN.cases.actionResult }}</a-select-option>
        <a-select-option v-for="item in RISK_ACTION_RESULTS" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      <a-date-picker v-model:value="filters.from" data-testid="filter-from" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-date-picker v-model:value="filters.to" data-testid="filter-to" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-button type="primary" v-auth="PERMS.RISK_CASE_QUERY" data-testid="case-query" @click="load">
        {{ zhCN.common.query }}
      </a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="hit-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.cases.hitType">
        <template #default="{ record: row }">{{ row.hitType }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.cases.ruleCode">
        <template #default="{ record: row }">{{ row.ruleCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.cases.userId">
        <template #default="{ record: row }">{{ row.userId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.cases.dimensionValue">
        <template #default="{ record: row }">{{ row.dimensionValue }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.cases.hitValue">
        <template #default="{ record: row }">{{ row.hitValue }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.cases.threshold">
        <template #default="{ record: row }">{{ row.threshold }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.cases.actionResult">
        <template #default="{ record: row }">{{ row.actionResult }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.cases.occurredAt">
        <template #default="{ record: row }">{{ formatDateTime(row.occurredAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.RISK_CASE_HANDLE" data-testid="case-handle" @click="openHandle(row)">
              {{ zhCN.cases.handle }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog
      :visible="handleOpen"
      :title="zhCN.cases.handle"
      :saving="saving"
      @submit="submitHandle"
      @cancel="handleOpen = false"
    >
      <a-form-item :label="zhCN.cases.userId">
        <a-input v-model:value="form.userId" data-testid="handle-user" />
      </a-form-item>
      <a-form-item :label="zhCN.cases.action">
        <a-select v-model:value="form.action" data-testid="handle-action">
        <a-select-option v-for="item in RISK_HANDLE_ACTIONS" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item v-if="form.action === 'REMOVE_BLACK'" :label="zhCN.cases.toWhitelist">
        <a-checkbox v-model:checked="form.toWhitelist" data-testid="handle-to-whitelist" />
      </a-form-item>
      <a-form-item v-if="form.action === 'ADD_BLACK'" :label="zhCN.cases.expireAt">
        <a-date-picker v-model:value="form.expireAt" data-testid="handle-expire" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
      <a-form-item :label="zhCN.cases.reason">
        <a-input v-model:value="form.reason" data-testid="handle-reason" required />
      </a-form-item>
    </FormDialog>
  </section>
</template>
