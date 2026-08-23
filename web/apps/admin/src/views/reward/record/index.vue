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
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { adminRowKey } from "@/utils/table";

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
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
  }
}

async function onFulfillConfirm(): Promise<void> {
  if (!recordId.value) {
    return;
  }
  const result = await fulfillConfirm(Number(recordId.value));
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
  }
}

async function onFulfillRetry(): Promise<void> {
  if (!recordId.value) {
    return;
  }
  const result = await fulfillRetry(Number(recordId.value));
  const parsed = writeOrFeedback(result);
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
  const parsed = writeOrFeedback(result);
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
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.categoryCode" data-testid="filter-category" :placeholder="zhCN.prize.category" />
      <a-input v-model:value="filters.prizeId" data-testid="filter-prize" :placeholder="zhCN.record.prizeId" />
      <a-date-picker v-model:value="filters.from" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-date-picker v-model:value="filters.to" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      <a-button type="primary" v-auth="PERMS.REWARD_RECORD_QUERY" data-testid="spend-query" @click="loadSpend">
        {{ zhCN.common.query }}
      </a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <h3>{{ zhCN.record.spend }}</h3>
    <a-table size="small" :loading="loading" :data-source="spendRows" class="data-table admin-table" data-testid="spend-table" :pagination="false" :row-key="adminRowKey">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.prize.category">
        <template #default="{ record: row }">{{ row.categoryCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.record.arrivedCount">
        <template #default="{ record: row }">{{ row.arrivedCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.record.arrivedCost">
        <template #default="{ record: row }">{{ row.arrivedCostFen }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.record.sendingCount">
        <template #default="{ record: row }">{{ row.sendingCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.record.sendingCost">
        <template #default="{ record: row }">{{ row.sendingCostFen }}</template>
      </a-table-column>
    </a-table>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="recordId" data-testid="record-id" :placeholder="zhCN.record.recordId" />
      <a-button v-auth="PERMS.REWARD_RECORD_RETRY" data-testid="record-retry" @click="onRetry">
        {{ zhCN.record.retry }}
      </a-button>
      <a-button v-auth="PERMS.REWARD_RECORD_FULFILL" data-testid="record-fulfill-confirm" @click="onFulfillConfirm">
        {{ zhCN.record.fulfillConfirm }}
      </a-button>
      <a-button v-auth="PERMS.REWARD_RECORD_FULFILL" data-testid="record-fulfill-retry" @click="onFulfillRetry">
        {{ zhCN.record.fulfillRetry }}
      </a-button>
    </a-form>
    <h3>{{ zhCN.record.manualGrant }}</h3>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="grantForm.userId" data-testid="grant-user" :placeholder="zhCN.record.userId" />
      <a-input v-model:value="grantForm.prizeId" data-testid="grant-prize" :placeholder="zhCN.record.prizeId" />
      <a-input v-model:value="grantForm.reason" data-testid="grant-reason" :placeholder="zhCN.prize.reason" />
      <label v-for="rule in BYPASS_RULES" :key="rule">
        <a-checkbox :checked="grantForm.bypass.includes(rule)" @update:checked="(val: boolean) => toggleBypass(rule, val)" />
        {{ rule }}
      </label>
      <a-button v-auth="PERMS.REWARD_RECORD_MANUAL" data-testid="grant-submit" @click="onManualGrant">
        {{ zhCN.record.manualGrant }}
      </a-button>
    </a-form>
  </section>
</template>
