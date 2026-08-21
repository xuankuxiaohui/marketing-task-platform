<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { kickSession, pageSessions, type SessionView } from "@/api/identity";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { ACCOUNT_TYPE, PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "SessionManagePage" });

const records = ref<SessionView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ accountType: "", account: "" });
const pendingKick = ref<SessionView | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageSessions({
    accountType: filters.accountType,
    account: filters.account,
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

async function onKickConfirm(): Promise<void> {
  const row = pendingKick.value;
  pendingKick.value = null;
  if (!row?.account || !row.accountType) {
    return;
  }
  const result = await kickSession({
    accountType: row.accountType,
    account: row.account,
    tokenLast4: row.tokenLast4,
  });
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="session-page">
    <h2>{{ zhCN.session.title }}</h2>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-select v-model="filters.accountType" data-testid="filter-account-type">
        <el-option value="" :label="zhCN.session.accountType" />
        <el-option :value="ACCOUNT_TYPE.ADMIN" :label="zhCN.session.typeAdmin" />
        <el-option :value="ACCOUNT_TYPE.PORTAL" :label="zhCN.session.typePortal" />
      </el-select>
      <el-input v-model="filters.account" data-testid="filter-account" :placeholder="zhCN.session.account" />
      <el-button data-testid="session-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="records" class="data-table" data-testid="session-table" stripe>
      <el-table-column :label="zhCN.session.account">
        <template #default="{ row }">{{ row.account }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.session.accountType">
        <template #default="{ row }">{{ row.accountType }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.session.tokenLast4">
        <template #default="{ row }">{{ row.tokenLast4 }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.session.loginAt">
        <template #default="{ row }">{{ formatDateTime(row.loginAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.session.lastActiveAt">
        <template #default="{ row }">{{ formatDateTime(row.lastActiveAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.session.ip">
        <template #default="{ row }">{{ row.ip }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.session.deviceId">
        <template #default="{ row }">{{ row.deviceId || "—" }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-auth="PERMS.SESSION_KICK" data-testid="session-kick" @click="pendingKick = row">
              {{ zhCN.session.kick }}
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
    <ConfirmDialog
      :visible="pendingKick != null"
      :message="zhCN.confirm.kick"
      @confirm="onKickConfirm"
      @cancel="pendingKick = null"
    />
  </section>
</template>
