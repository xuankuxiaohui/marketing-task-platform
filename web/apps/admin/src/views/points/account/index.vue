<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { adjustPoints, pagePointsAccounts, type PointsAccountView } from "@/api/reward";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "PointsAccountPage" });

const records = ref<PointsAccountView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const userId = ref("");
const formOpen = ref(false);
const saving = ref(false);
const form = reactive({ userId: "", amount: "", reason: "" });

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pagePointsAccounts({
    userId: userId.value ? Number(userId.value) : undefined,
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

function openAdjust(row?: PointsAccountView): void {
  form.userId = row?.userId != null ? String(row.userId) : "";
  form.amount = "";
  form.reason = "";
  formOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  const result = await adjustPoints({
    userId: Number(form.userId),
    amount: Number(form.amount),
    reason: form.reason,
  });
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
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
  <section class="admin-page" data-testid="points-account-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.points.accountTitle }}</h2>
      <a-button type="primary" v-auth="PERMS.POINTS_ACCOUNT_ADJUST" data-testid="account-adjust" @click="openAdjust()">
        {{ zhCN.points.adjust }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="userId" data-testid="filter-user" :placeholder="zhCN.points.userId" />
      <a-button type="primary" data-testid="account-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="account-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.POINTS_ACCOUNT_ADJUST" type="primary" size="small" @click="openAdjust()">
        {{ zhCN.points.adjust }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.points.userId">
        <template #default="{ record: row }">{{ row.userId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.points.balance">
        <template #default="{ record: row }">{{ row.balance }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.POINTS_ACCOUNT_ADJUST" data-testid="row-adjust" @click="openAdjust(row)">
              {{ zhCN.points.adjust }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog :visible="formOpen" :title="zhCN.points.adjust" :saving="saving" @submit="submit" @cancel="formOpen = false">
      <a-form-item :label="zhCN.points.userId">
        <a-input v-model:value="form.userId" data-testid="adjust-user" required />
      </a-form-item>
      <a-form-item :label="zhCN.points.amount">
        <a-input v-model:value="form.amount" data-testid="adjust-amount" required />
      </a-form-item>
      <a-form-item :label="zhCN.points.reason">
        <a-input v-model:value="form.reason" data-testid="adjust-reason" required />
      </a-form-item>
    </FormDialog>
  </section>
</template>
