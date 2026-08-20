<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  fetchSpend,
  fulfillConfirm,
  fulfillRetry,
  manualGrant,
  retryGrant,
  type SpendRowView,
} from "@/api/reward";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { PERMS } from "@/constants/identity";
import { BYPASS_RULES } from "@/constants/reward";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "RewardRecordPage" });

const spendRows = ref<SpendRowView[]>([]);
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ categoryCode: "", prizeId: "", from: "", to: "" });
const recordId = ref("");
const grantForm = reactive({
  userId: "",
  prizeId: "",
  reason: "",
  bypass: [] as string[],
});

async function loadSpend(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await fetchSpend({
    categoryCode: filters.categoryCode,
    prizeId: filters.prizeId ? Number(filters.prizeId) : undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
  });
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  spendRows.value = parsed.data?.rows ?? [];
}

function toggleBypass(rule: string, checked: boolean): void {
  if (checked) {
    grantForm.bypass = [...grantForm.bypass, rule];
    return;
  }
  grantForm.bypass = grantForm.bypass.filter((item) => item !== rule);
}

async function onRetry(): Promise<void> {
  if (!recordId.value) {
    return;
  }
  const result = await retryGrant(Number(recordId.value));
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
  }
}

async function onFulfillConfirm(): Promise<void> {
  if (!recordId.value) {
    return;
  }
  const result = await fulfillConfirm(Number(recordId.value));
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
  }
}

async function onFulfillRetry(): Promise<void> {
  if (!recordId.value) {
    return;
  }
  const result = await fulfillRetry(Number(recordId.value));
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
  }
}

async function onManualGrant(): Promise<void> {
  const result = await manualGrant({
    userId: Number(grantForm.userId),
    prizeId: Number(grantForm.prizeId),
    reason: grantForm.reason,
    bypassRules: grantForm.bypass,
  });
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await loadSpend();
}

onMounted(() => {
  void loadSpend();
});
</script>

<template>
  <section class="admin-page" data-testid="record-page">
    <h2>{{ zhCN.record.title }}</h2>
    <p class="hint" data-testid="record-no-list">{{ zhCN.record.noListHint }}</p>
    <div class="admin-toolbar">
      <input v-model="filters.categoryCode" data-testid="filter-category" :placeholder="zhCN.prize.category" />
      <input v-model="filters.prizeId" data-testid="filter-prize" :placeholder="zhCN.record.prizeId" />
      <input v-model="filters.from" type="datetime-local" />
      <input v-model="filters.to" type="datetime-local" />
      <button v-auth="PERMS.REWARD_RECORD_QUERY" type="button" data-testid="spend-query" @click="loadSpend">
        {{ zhCN.common.query }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <h3>{{ zhCN.record.spend }}</h3>
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="spendRows.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="spend-table">
      <thead>
        <tr>
          <th>{{ zhCN.prize.category }}</th>
          <th>{{ zhCN.record.arrivedCount }}</th>
          <th>{{ zhCN.record.arrivedCost }}</th>
          <th>{{ zhCN.record.sendingCount }}</th>
          <th>{{ zhCN.record.sendingCost }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in spendRows" :key="row.categoryCode">
          <td>{{ row.categoryCode }}</td>
          <td>{{ row.arrivedCount }}</td>
          <td>{{ row.arrivedCostFen }}</td>
          <td>{{ row.sendingCount }}</td>
          <td>{{ row.sendingCostFen }}</td>
        </tr>
      </tbody>
    </table>
    <div class="admin-toolbar">
      <input v-model="recordId" data-testid="record-id" :placeholder="zhCN.record.recordId" />
      <button v-auth="PERMS.REWARD_RECORD_RETRY" type="button" data-testid="record-retry" @click="onRetry">
        {{ zhCN.record.retry }}
      </button>
      <button v-auth="PERMS.REWARD_RECORD_FULFILL" type="button" data-testid="record-fulfill-confirm" @click="onFulfillConfirm">
        {{ zhCN.record.fulfillConfirm }}
      </button>
      <button v-auth="PERMS.REWARD_RECORD_FULFILL" type="button" data-testid="record-fulfill-retry" @click="onFulfillRetry">
        {{ zhCN.record.fulfillRetry }}
      </button>
    </div>
    <h3>{{ zhCN.record.manualGrant }}</h3>
    <div class="admin-toolbar">
      <input v-model="grantForm.userId" data-testid="grant-user" :placeholder="zhCN.record.userId" />
      <input v-model="grantForm.prizeId" data-testid="grant-prize" :placeholder="zhCN.record.prizeId" />
      <input v-model="grantForm.reason" data-testid="grant-reason" :placeholder="zhCN.prize.reason" />
      <label v-for="rule in BYPASS_RULES" :key="rule">
        <input
          type="checkbox"
          :checked="grantForm.bypass.includes(rule)"
          @change="toggleBypass(rule, ($event.target as HTMLInputElement).checked)"
        />
        {{ rule }}
      </label>
      <button v-auth="PERMS.REWARD_RECORD_MANUAL" type="button" data-testid="grant-submit" @click="onManualGrant">
        {{ zhCN.record.manualGrant }}
      </button>
    </div>
  </section>
</template>
