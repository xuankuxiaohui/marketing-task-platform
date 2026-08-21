<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  actionReconItem,
  createReconBatch,
  importReconLines,
  matchReconBatch,
  pageReconBatches,
  pageReconItems,
  reviewReconItem,
  type ReconBatchResponse,
  type ReconImportCommand,
  type ReconItemView,
} from "@/api/reward";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { RECON_ACTIONS, RECON_REVIEW } from "@/constants/reward";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "RewardReconPage" });

const batches = ref<ReconBatchResponse[]>([]);
const items = ref<ReconItemView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ categoryCode: "", billDate: "", status: "" });
const selected = ref<ReconBatchResponse | null>(null);
const createOpen = ref(false);
const importOpen = ref(false);
const actionOpen = ref(false);
const reviewOpen = ref(false);
const saving = ref(false);
const createForm = reactive({ categoryCode: "", billDate: "" });
const importJson = ref("[]");
const actionItem = ref<ReconItemView | null>(null);
const actionForm = reactive({ action: "ABSORB", reason: "", userId: "", prizeId: "" });
const reviewForm = reactive({ decision: "CONFIRM", remark: "" });

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageReconBatches({
    categoryCode: filters.categoryCode,
    billDate: filters.billDate || undefined,
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
  batches.value = parsed.data?.records ?? [];
  total.value = parsed.data?.total ?? 0;
}

async function loadItems(row: ReconBatchResponse): Promise<void> {
  if (row.id == null) {
    return;
  }
  selected.value = row;
  const result = await pageReconItems(row.id, { page: 1, pageSize: 50 });
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  items.value = parsed.data?.records ?? [];
}

async function submitCreate(): Promise<void> {
  saving.value = true;
  const result = await createReconBatch({ categoryCode: createForm.categoryCode, billDate: createForm.billDate });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  createOpen.value = false;
  await load();
}

async function submitImport(): Promise<void> {
  if (selected.value?.id == null) {
    return;
  }
  let body: ReconImportCommand;
  try {
    body = { lines: JSON.parse(importJson.value) as ReconImportCommand["lines"] };
  } catch {
    feedback.value = { message: "JSON 非法" };
    return;
  }
  saving.value = true;
  const result = await importReconLines(selected.value.id, body);
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  importOpen.value = false;
  await loadItems(selected.value);
}

async function onMatch(row: ReconBatchResponse): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await matchReconBatch(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
  await loadItems(row);
}

function openReview(row: ReconItemView): void {
  actionItem.value = row;
  reviewForm.decision = "CONFIRM";
  reviewForm.remark = "";
  reviewOpen.value = true;
}

function openAction(row: ReconItemView): void {
  actionItem.value = row;
  actionForm.action = "ABSORB";
  actionForm.reason = "";
  actionForm.userId = "";
  actionForm.prizeId = "";
  actionOpen.value = true;
}

async function submitReview(): Promise<void> {
  if (actionItem.value?.id == null) {
    return;
  }
  saving.value = true;
  const result = await reviewReconItem(actionItem.value.id, {
    decision: reviewForm.decision,
    remark: reviewForm.remark,
  });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  reviewOpen.value = false;
  if (selected.value) {
    await loadItems(selected.value);
  }
}

async function submitAction(): Promise<void> {
  if (actionItem.value?.id == null) {
    return;
  }
  saving.value = true;
  const result = await actionReconItem(actionItem.value.id, {
    action: actionForm.action,
    reason: actionForm.reason || undefined,
    userId: actionForm.userId ? Number(actionForm.userId) : undefined,
    prizeId: actionForm.prizeId ? Number(actionForm.prizeId) : undefined,
  });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  actionOpen.value = false;
  if (selected.value) {
    await loadItems(selected.value);
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="recon-page">
    <h2>{{ zhCN.recon.title }}</h2>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.categoryCode" data-testid="filter-category" :placeholder="zhCN.recon.category" />
      <el-input v-model="filters.billDate" data-testid="filter-bill" type="date" />
      <el-button data-testid="recon-query" @click="load">{{ zhCN.common.query }}</el-button>
      <el-button v-auth="PERMS.REWARD_RECON_IMPORT" data-testid="recon-create" @click="createOpen = true">
        {{ zhCN.recon.createBatch }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="batches.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="batches" class="data-table" data-testid="recon-table" stripe>
      <el-table-column label="ID">
        <template #default="{ row }">{{ row.id }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.recon.category">
        <template #default="{ row }">{{ row.categoryCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.recon.billDate">
        <template #default="{ row }">{{ row.billDate }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-auth="PERMS.REWARD_RECON_QUERY" data-testid="recon-items" @click="loadItems(row)">
              {{ zhCN.recon.items }}
            </el-button>
            <el-button v-auth="PERMS.REWARD_RECON_IMPORT" data-testid="recon-import" @click="selected = row; importOpen = true">
              {{ zhCN.recon.import }}
            </el-button>
            <el-button v-auth="PERMS.REWARD_RECON_MATCH" data-testid="recon-match" @click="onMatch(row)">
              {{ zhCN.recon.match }}
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
    <el-table :data="items" class="data-table" stripe>
      <el-table-column label="ID">
        <template #default="{ row }">{{ row.id }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.recon.result">
        <template #default="{ row }">{{ row.result }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.recon.reviewStatus">
        <template #default="{ row }">{{ row.reviewStatus }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button
              v-if="row.reviewStatus === RECON_REVIEW.PENDING_REVIEW"
              v-auth="PERMS.REWARD_RECON_ACTION"
              data-testid="recon-review"
              @click="openReview(row)"
            >
              {{ zhCN.recon.review }}
            </el-button>
            <el-button v-auth="PERMS.REWARD_RECON_ACTION" data-testid="recon-action" @click="openAction(row)">
              {{ zhCN.recon.action }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <FormDialog :visible="createOpen" :title="zhCN.recon.createBatch" :saving="saving" @submit="submitCreate" @cancel="createOpen = false">
      <el-form-item :label="zhCN.recon.category">
        <el-input v-model="createForm.categoryCode" data-testid="batch-category" required />
      </el-form-item>
      <el-form-item :label="zhCN.recon.billDate">
        <el-input v-model="createForm.billDate" data-testid="batch-date" type="date" required />
      </el-form-item>
    </FormDialog>
    <FormDialog :visible="importOpen" :title="zhCN.recon.import" :saving="saving" @submit="submitImport" @cancel="importOpen = false">
      <el-form-item :label="zhCN.recon.lines">
        <el-input v-model="importJson" type="textarea" data-testid="import-json" :rows="6"  />
      </el-form-item>
    </FormDialog>
    <FormDialog :visible="reviewOpen" :title="zhCN.recon.review" :saving="saving" @submit="submitReview" @cancel="reviewOpen = false">
      <el-form-item :label="zhCN.recon.decision">
        <el-select v-model="reviewForm.decision" data-testid="review-decision">
        <el-option value="CONFIRM" :label="zhCN.recon.confirmChannel" />
        <el-option value="REJECT" :label="zhCN.recon.rejectChannel" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.recon.remark">
        <el-input v-model="reviewForm.remark" data-testid="review-remark" required />
      </el-form-item>
    </FormDialog>
    <FormDialog :visible="actionOpen" :title="zhCN.recon.action" :saving="saving" @submit="submitAction" @cancel="actionOpen = false">
      <el-form-item :label="zhCN.recon.action">
        <el-select v-model="actionForm.action" data-testid="action-type">
        <el-option v-for="item in RECON_ACTIONS" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.prize.reason">
        <el-input v-model="actionForm.reason" data-testid="action-reason" />
      </el-form-item>
      <el-form-item v-if="actionForm.action === 'MANUAL_GRANT'" :label="zhCN.record.userId">
        <el-input v-model="actionForm.userId" />
      </el-form-item>
      <el-form-item v-if="actionForm.action === 'MANUAL_GRANT'" :label="zhCN.record.prizeId">
        <el-input v-model="actionForm.prizeId" />
      </el-form-item>
    </FormDialog>
  </section>
</template>
