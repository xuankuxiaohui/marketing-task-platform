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
    <h2>{{ zhCN.cases.title }}</h2>
    <div class="admin-toolbar">
      <select v-model="filters.ruleCode" data-testid="filter-rule">
        <option value="">{{ zhCN.cases.ruleCode }}</option>
        <option v-for="item in RISK_RULE_CODES" :key="item" :value="item">{{ item }}</option>
      </select>
      <select v-model="filters.hitType" data-testid="filter-hit-type">
        <option value="">{{ zhCN.cases.hitType }}</option>
        <option v-for="item in RISK_HIT_TYPES" :key="item" :value="item">{{ item }}</option>
      </select>
      <input v-model="filters.dimensionValue" data-testid="filter-dimension" :placeholder="zhCN.cases.dimensionValue" />
      <input v-model="filters.userId" data-testid="filter-user" :placeholder="zhCN.cases.userId" />
      <select v-model="filters.actionResult" data-testid="filter-action-result">
        <option value="">{{ zhCN.cases.actionResult }}</option>
        <option v-for="item in RISK_ACTION_RESULTS" :key="item" :value="item">{{ item }}</option>
      </select>
      <input v-model="filters.from" data-testid="filter-from" type="datetime-local" />
      <input v-model="filters.to" data-testid="filter-to" type="datetime-local" />
      <button v-auth="PERMS.RISK_CASE_QUERY" type="button" data-testid="case-query" @click="load">
        {{ zhCN.common.query }}
      </button>
      <button v-auth="PERMS.RISK_CASE_HANDLE" type="button" data-testid="case-handle-open" @click="openHandle()">
        {{ zhCN.cases.handle }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="hit-table">
      <thead>
        <tr>
          <th>{{ zhCN.cases.hitType }}</th>
          <th>{{ zhCN.cases.ruleCode }}</th>
          <th>{{ zhCN.cases.userId }}</th>
          <th>{{ zhCN.cases.dimensionValue }}</th>
          <th>{{ zhCN.cases.hitValue }}</th>
          <th>{{ zhCN.cases.threshold }}</th>
          <th>{{ zhCN.cases.actionResult }}</th>
          <th>{{ zhCN.cases.occurredAt }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.hitType }}</td>
          <td>{{ row.ruleCode }}</td>
          <td>{{ row.userId }}</td>
          <td>{{ row.dimensionValue }}</td>
          <td>{{ row.hitValue }}</td>
          <td>{{ row.threshold }}</td>
          <td>{{ row.actionResult }}</td>
          <td>{{ formatDateTime(row.occurredAt) }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.RISK_CASE_HANDLE" type="button" data-testid="case-handle" @click="openHandle(row)">
              {{ zhCN.cases.handle }}
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
      :visible="handleOpen"
      :title="zhCN.cases.handle"
      :saving="saving"
      @submit="submitHandle"
      @cancel="handleOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.cases.userId }}</span>
        <input v-model="form.userId" data-testid="handle-user" />
      </label>
      <label class="field">
        <span>{{ zhCN.cases.action }}</span>
        <select v-model="form.action" data-testid="handle-action">
          <option v-for="item in RISK_HANDLE_ACTIONS" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label v-if="form.action === 'REMOVE_BLACK'" class="field">
        <span>{{ zhCN.cases.toWhitelist }}</span>
        <input v-model="form.toWhitelist" data-testid="handle-to-whitelist" type="checkbox" />
      </label>
      <label v-if="form.action === 'ADD_BLACK'" class="field">
        <span>{{ zhCN.cases.expireAt }}</span>
        <input v-model="form.expireAt" data-testid="handle-expire" type="datetime-local" />
      </label>
      <label class="field">
        <span>{{ zhCN.cases.reason }}</span>
        <input v-model="form.reason" data-testid="handle-reason" required />
      </label>
    </FormDialog>
  </section>
</template>
