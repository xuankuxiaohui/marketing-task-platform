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
    <h2>{{ zhCN.points.accountTitle }}</h2>
    <div class="admin-toolbar">
      <input v-model="userId" data-testid="filter-user" :placeholder="zhCN.points.userId" />
      <button type="button" data-testid="account-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.POINTS_ACCOUNT_ADJUST" type="button" data-testid="account-adjust" @click="openAdjust()">
        {{ zhCN.points.adjust }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="account-table">
      <thead>
        <tr>
          <th>{{ zhCN.points.userId }}</th>
          <th>{{ zhCN.points.balance }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.userId }}</td>
          <td>{{ row.balance }}</td>
          <td>{{ formatDateTime(row.createdAt) }}</td>
          <td>
            <button v-auth="PERMS.POINTS_ACCOUNT_ADJUST" type="button" data-testid="row-adjust" @click="openAdjust(row)">
              {{ zhCN.points.adjust }}
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
    <FormDialog :visible="formOpen" :title="zhCN.points.adjust" :saving="saving" @submit="submit" @cancel="formOpen = false">
      <label class="field">
        <span>{{ zhCN.points.userId }}</span>
        <input v-model="form.userId" data-testid="adjust-user" required />
      </label>
      <label class="field">
        <span>{{ zhCN.points.amount }}</span>
        <input v-model="form.amount" data-testid="adjust-amount" required />
      </label>
      <label class="field">
        <span>{{ zhCN.points.reason }}</span>
        <input v-model="form.reason" data-testid="adjust-reason" required />
      </label>
    </FormDialog>
  </section>
</template>
