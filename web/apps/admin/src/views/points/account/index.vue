<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { adjustPoints, pagePointsAccounts, type PointsAccountView } from "@/api/reward";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "PointsAccountPage" });

const records = ref<PointsAccountView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="points-account-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.points.accountTitle }}</h2>
      <el-button type="primary" v-auth="PERMS.POINTS_ACCOUNT_ADJUST" data-testid="account-adjust" @click="openAdjust()">
        {{ zhCN.points.adjust }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="userId" data-testid="filter-user" :placeholder="zhCN.points.userId" />
      <el-button data-testid="account-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.POINTS_ACCOUNT_ADJUST" text type="primary" @click="openAdjust()">
        {{ zhCN.points.adjust }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="account-table" size="small" stripe>
      <el-table-column :label="zhCN.points.userId">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.points.balance">
        <template #default="{ row }">{{ row.balance }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.createdAt">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.POINTS_ACCOUNT_ADJUST" data-testid="row-adjust" @click="openAdjust(row)">
              {{ zhCN.points.adjust }}
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
    <FormDialog :visible="formOpen" :title="zhCN.points.adjust" :saving="saving" @submit="submit" @cancel="formOpen = false">
      <el-form-item :label="zhCN.points.userId">
        <el-input v-model="form.userId" data-testid="adjust-user" required />
      </el-form-item>
      <el-form-item :label="zhCN.points.amount">
        <el-input v-model="form.amount" data-testid="adjust-amount" required />
      </el-form-item>
      <el-form-item :label="zhCN.points.reason">
        <el-input v-model="form.reason" data-testid="adjust-reason" required />
      </el-form-item>
    </FormDialog>
  </section>
</template>
