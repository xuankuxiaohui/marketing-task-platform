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
    <div class="admin-toolbar">
      <input v-model="filters.categoryCode" data-testid="filter-category" :placeholder="zhCN.recon.category" />
      <input v-model="filters.billDate" data-testid="filter-bill" type="date" />
      <button type="button" data-testid="recon-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.REWARD_RECON_IMPORT" type="button" data-testid="recon-create" @click="createOpen = true">
        {{ zhCN.recon.createBatch }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="batches.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="recon-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>{{ zhCN.recon.category }}</th>
          <th>{{ zhCN.recon.billDate }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in batches" :key="row.id">
          <td>{{ row.id }}</td>
          <td>{{ row.categoryCode }}</td>
          <td>{{ row.billDate }}</td>
          <td>{{ row.status }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.REWARD_RECON_QUERY" type="button" data-testid="recon-items" @click="loadItems(row)">
              {{ zhCN.recon.items }}
            </button>
            <button v-auth="PERMS.REWARD_RECON_IMPORT" type="button" data-testid="recon-import" @click="selected = row; importOpen = true">
              {{ zhCN.recon.import }}
            </button>
            <button v-auth="PERMS.REWARD_RECON_MATCH" type="button" data-testid="recon-match" @click="onMatch(row)">
              {{ zhCN.recon.match }}
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
    <table v-if="items.length > 0" class="data-table" data-testid="recon-item-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>{{ zhCN.recon.result }}</th>
          <th>{{ zhCN.recon.reviewStatus }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in items" :key="row.id">
          <td>{{ row.id }}</td>
          <td>{{ row.result }}</td>
          <td>{{ row.reviewStatus }}</td>
          <td class="row-actions">
            <button
              v-if="row.reviewStatus === RECON_REVIEW.PENDING_REVIEW"
              v-auth="PERMS.REWARD_RECON_ACTION"
              type="button"
              data-testid="recon-review"
              @click="openReview(row)"
            >
              {{ zhCN.recon.review }}
            </button>
            <button v-auth="PERMS.REWARD_RECON_ACTION" type="button" data-testid="recon-action" @click="openAction(row)">
              {{ zhCN.recon.action }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <FormDialog :visible="createOpen" :title="zhCN.recon.createBatch" :saving="saving" @submit="submitCreate" @cancel="createOpen = false">
      <label class="field">
        <span>{{ zhCN.recon.category }}</span>
        <input v-model="createForm.categoryCode" data-testid="batch-category" required />
      </label>
      <label class="field">
        <span>{{ zhCN.recon.billDate }}</span>
        <input v-model="createForm.billDate" data-testid="batch-date" type="date" required />
      </label>
    </FormDialog>
    <FormDialog :visible="importOpen" :title="zhCN.recon.import" :saving="saving" @submit="submitImport" @cancel="importOpen = false">
      <label class="field">
        <span>{{ zhCN.recon.lines }}</span>
        <textarea v-model="importJson" data-testid="import-json" rows="6" />
      </label>
    </FormDialog>
    <FormDialog :visible="reviewOpen" :title="zhCN.recon.review" :saving="saving" @submit="submitReview" @cancel="reviewOpen = false">
      <label class="field">
        <span>{{ zhCN.recon.decision }}</span>
        <select v-model="reviewForm.decision" data-testid="review-decision">
          <option value="CONFIRM">{{ zhCN.recon.confirmChannel }}</option>
          <option value="REJECT">{{ zhCN.recon.rejectChannel }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.recon.remark }}</span>
        <input v-model="reviewForm.remark" data-testid="review-remark" required />
      </label>
    </FormDialog>
    <FormDialog :visible="actionOpen" :title="zhCN.recon.action" :saving="saving" @submit="submitAction" @cancel="actionOpen = false">
      <label class="field">
        <span>{{ zhCN.recon.action }}</span>
        <select v-model="actionForm.action" data-testid="action-type">
          <option v-for="item in RECON_ACTIONS" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.prize.reason }}</span>
        <input v-model="actionForm.reason" data-testid="action-reason" />
      </label>
      <label v-if="actionForm.action === 'MANUAL_GRANT'" class="field">
        <span>{{ zhCN.record.userId }}</span>
        <input v-model="actionForm.userId" />
      </label>
      <label v-if="actionForm.action === 'MANUAL_GRANT'" class="field">
        <span>{{ zhCN.record.prizeId }}</span>
        <input v-model="actionForm.prizeId" />
      </label>
    </FormDialog>
  </section>
</template>
