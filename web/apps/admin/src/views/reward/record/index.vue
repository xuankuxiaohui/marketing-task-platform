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
    <div class="admin-page__header">
      <h2>{{ zhCN.record.title }}</h2>
    </div>
    <p class="hint" data-testid="record-no-list">{{ zhCN.record.noListHint }}</p>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.categoryCode" data-testid="filter-category" :placeholder="zhCN.prize.category" />
      <el-input v-model="filters.prizeId" data-testid="filter-prize" :placeholder="zhCN.record.prizeId" />
      <el-input v-model="filters.from" type="datetime-local" />
      <el-input v-model="filters.to" type="datetime-local" />
      <el-button v-auth="PERMS.REWARD_RECORD_QUERY" data-testid="spend-query" @click="loadSpend">
        {{ zhCN.common.query }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <h3>{{ zhCN.record.spend }}</h3>
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="spendRows.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
    </div>
    <el-table v-else :data="spendRows" class="data-table admin-table" data-testid="spend-table" size="small" stripe>
      <el-table-column :label="zhCN.prize.category">
        <template #default="{ row }">{{ row.categoryCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.record.arrivedCount">
        <template #default="{ row }">{{ row.arrivedCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.record.arrivedCost">
        <template #default="{ row }">{{ row.arrivedCostFen }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.record.sendingCount">
        <template #default="{ row }">{{ row.sendingCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.record.sendingCost">
        <template #default="{ row }">{{ row.sendingCostFen }}</template>
      </el-table-column>
    </el-table>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="recordId" data-testid="record-id" :placeholder="zhCN.record.recordId" />
      <el-button v-auth="PERMS.REWARD_RECORD_RETRY" data-testid="record-retry" @click="onRetry">
        {{ zhCN.record.retry }}
      </el-button>
      <el-button v-auth="PERMS.REWARD_RECORD_FULFILL" data-testid="record-fulfill-confirm" @click="onFulfillConfirm">
        {{ zhCN.record.fulfillConfirm }}
      </el-button>
      <el-button v-auth="PERMS.REWARD_RECORD_FULFILL" data-testid="record-fulfill-retry" @click="onFulfillRetry">
        {{ zhCN.record.fulfillRetry }}
      </el-button>
    </el-form>
    <h3>{{ zhCN.record.manualGrant }}</h3>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="grantForm.userId" data-testid="grant-user" :placeholder="zhCN.record.userId" />
      <el-input v-model="grantForm.prizeId" data-testid="grant-prize" :placeholder="zhCN.record.prizeId" />
      <el-input v-model="grantForm.reason" data-testid="grant-reason" :placeholder="zhCN.prize.reason" />
      <label v-for="rule in BYPASS_RULES" :key="rule">
        <el-checkbox
          :model-value="grantForm.bypass.includes(rule)"
          @update:model-value="(val: boolean) => toggleBypass(rule, val)" />
        {{ rule }}
      </label>
      <el-button v-auth="PERMS.REWARD_RECORD_MANUAL" data-testid="grant-submit" @click="onManualGrant">
        {{ zhCN.record.manualGrant }}
      </el-button>
    </el-form>
  </section>
</template>
