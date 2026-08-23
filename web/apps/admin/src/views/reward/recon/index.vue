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
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "RewardReconPage" });

const batches = ref<ReconBatchResponse[]>([]);
const items = ref<ReconItemView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="recon-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.recon.title }}</h2>
      <a-button type="primary" v-auth="PERMS.REWARD_RECON_IMPORT" data-testid="recon-create" @click="createOpen = true">
        {{ zhCN.recon.createBatch }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.categoryCode" data-testid="filter-category" :placeholder="zhCN.recon.category" />
      <a-date-picker v-model:value="filters.billDate" data-testid="filter-bill" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
      <a-button type="primary" data-testid="recon-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="batches" class="data-table admin-table" data-testid="recon-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.REWARD_RECON_IMPORT" type="primary" size="small" @click="createOpen = true">
        {{ zhCN.recon.createBatch }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column title="ID">
        <template #default="{ record: row }">{{ row.id }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.recon.category">
        <template #default="{ record: row }">{{ row.categoryCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.recon.billDate">
        <template #default="{ record: row }">{{ row.billDate }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.REWARD_RECON_QUERY" data-testid="recon-items" @click="loadItems(row)">
              {{ zhCN.recon.items }}
            </a-button>
            <a-button size="small" v-auth="PERMS.REWARD_RECON_IMPORT" data-testid="recon-import" @click="selected = row; importOpen = true">
              {{ zhCN.recon.import }}
            </a-button>
            <a-button size="small" v-auth="PERMS.REWARD_RECON_MATCH" data-testid="recon-match" @click="onMatch(row)">
              {{ zhCN.recon.match }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <a-table :data-source="items" class="data-table admin-table" size="small" :pagination="false" :row-key="adminRowKey">
      <a-table-column title="ID">
        <template #default="{ record: row }">{{ row.id }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.recon.result">
        <template #default="{ record: row }">{{ row.result }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.recon.reviewStatus">
        <template #default="{ record: row }">{{ row.reviewStatus }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-if="row.reviewStatus === RECON_REVIEW.PENDING_REVIEW" v-auth="PERMS.REWARD_RECON_ACTION" data-testid="recon-review" @click="openReview(row)">
              {{ zhCN.recon.review }}
            </a-button>
            <a-button size="small" v-auth="PERMS.REWARD_RECON_ACTION" data-testid="recon-action" @click="openAction(row)">
              {{ zhCN.recon.action }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog :visible="createOpen" :title="zhCN.recon.createBatch" :saving="saving" @submit="submitCreate" @cancel="createOpen = false">
      <a-form-item :label="zhCN.recon.category">
        <a-input v-model:value="createForm.categoryCode" data-testid="batch-category" required />
      </a-form-item>
      <a-form-item :label="zhCN.recon.billDate">
        <a-date-picker v-model:value="createForm.billDate" data-testid="batch-date" required value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
      </a-form-item>
    </FormDialog>
    <FormDialog :visible="importOpen" :title="zhCN.recon.import" :saving="saving" @submit="submitImport" @cancel="importOpen = false">
      <a-form-item :label="zhCN.recon.lines">
        <a-textarea v-model:value="importJson" data-testid="import-json" :rows="6" />
      </a-form-item>
    </FormDialog>
    <FormDialog :visible="reviewOpen" :title="zhCN.recon.review" :saving="saving" @submit="submitReview" @cancel="reviewOpen = false">
      <a-form-item :label="zhCN.recon.decision">
        <a-select v-model:value="reviewForm.decision" data-testid="review-decision">
        <a-select-option value="CONFIRM">{{ zhCN.recon.confirmChannel }}</a-select-option>
        <a-select-option value="REJECT">{{ zhCN.recon.rejectChannel }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.recon.remark">
        <a-input v-model:value="reviewForm.remark" data-testid="review-remark" required />
      </a-form-item>
    </FormDialog>
    <FormDialog :visible="actionOpen" :title="zhCN.recon.action" :saving="saving" @submit="submitAction" @cancel="actionOpen = false">
      <a-form-item :label="zhCN.recon.action">
        <a-select v-model:value="actionForm.action" data-testid="action-type">
        <a-select-option v-for="item in RECON_ACTIONS" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.prize.reason">
        <a-input v-model:value="actionForm.reason" data-testid="action-reason" />
      </a-form-item>
      <a-form-item v-if="actionForm.action === 'MANUAL_GRANT'" :label="zhCN.record.userId">
        <a-input v-model:value="actionForm.userId" />
      </a-form-item>
      <a-form-item v-if="actionForm.action === 'MANUAL_GRANT'" :label="zhCN.record.prizeId">
        <a-input v-model:value="actionForm.prizeId" />
      </a-form-item>
    </FormDialog>
  </section>
</template>
