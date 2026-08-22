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

defineOptions({ name: "RiskCasePage" });

const records = ref<RiskHitLogResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="case-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.cases.title }}</h2>
      <el-button type="primary" v-auth="PERMS.RISK_CASE_HANDLE" data-testid="case-handle-open" @click="openHandle()">
        {{ zhCN.cases.handle }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-select v-model="filters.ruleCode" data-testid="filter-rule">
        <el-option value="" :label="zhCN.cases.ruleCode" />
        <el-option v-for="item in RISK_RULE_CODES" :key="item" :value="item" :label="item" />
      </el-select>
      <el-select v-model="filters.hitType" data-testid="filter-hit-type">
        <el-option value="" :label="zhCN.cases.hitType" />
        <el-option v-for="item in RISK_HIT_TYPES" :key="item" :value="item" :label="item" />
      </el-select>
      <el-input v-model="filters.dimensionValue" data-testid="filter-dimension" :placeholder="zhCN.cases.dimensionValue" />
      <el-input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.cases.userId" />
      <el-select v-model="filters.actionResult" data-testid="filter-action-result">
        <el-option value="" :label="zhCN.cases.actionResult" />
        <el-option v-for="item in RISK_ACTION_RESULTS" :key="item" :value="item" :label="item" />
      </el-select>
      <el-input v-model="filters.from" data-testid="filter-from" type="datetime-local" />
      <el-input v-model="filters.to" data-testid="filter-to" type="datetime-local" />
      <el-button v-auth="PERMS.RISK_CASE_QUERY" data-testid="case-query" @click="load">
        {{ zhCN.common.query }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="hit-table" size="small" stripe>
      <el-table-column :label="zhCN.cases.hitType">
        <template #default="{ row }">{{ row.hitType }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.cases.ruleCode">
        <template #default="{ row }">{{ row.ruleCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.cases.userId">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.cases.dimensionValue">
        <template #default="{ row }">{{ row.dimensionValue }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.cases.hitValue">
        <template #default="{ row }">{{ row.hitValue }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.cases.threshold">
        <template #default="{ row }">{{ row.threshold }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.cases.actionResult">
        <template #default="{ row }">{{ row.actionResult }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.cases.occurredAt">
        <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.RISK_CASE_HANDLE" data-testid="case-handle" @click="openHandle(row)">
              {{ zhCN.cases.handle }}
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
      :visible="handleOpen"
      :title="zhCN.cases.handle"
      :saving="saving"
      @submit="submitHandle"
      @cancel="handleOpen = false"
    >
      <el-form-item :label="zhCN.cases.userId">
        <el-input v-model="form.userId" data-testid="handle-user" />
      </el-form-item>
      <el-form-item :label="zhCN.cases.action">
        <el-select v-model="form.action" data-testid="handle-action">
        <el-option v-for="item in RISK_HANDLE_ACTIONS" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item v-if="form.action === 'REMOVE_BLACK'" :label="zhCN.cases.toWhitelist">
        <el-checkbox v-model="form.toWhitelist" data-testid="handle-to-whitelist" />
      </el-form-item>
      <el-form-item v-if="form.action === 'ADD_BLACK'" :label="zhCN.cases.expireAt">
        <el-input v-model="form.expireAt" data-testid="handle-expire" type="datetime-local" />
      </el-form-item>
      <el-form-item :label="zhCN.cases.reason">
        <el-input v-model="form.reason" data-testid="handle-reason" required />
      </el-form-item>
    </FormDialog>
  </section>
</template>
