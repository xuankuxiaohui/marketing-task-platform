<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { kickSession, pageSessions, type SessionView } from "@/api/identity";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { ACCOUNT_TYPE, PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "SessionManagePage" });

const records = ref<SessionView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="session-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.session.title }}</h2>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-select v-model:value="filters.accountType" data-testid="filter-account-type">
        <a-select-option value="">{{ zhCN.session.accountType }}</a-select-option>
        <a-select-option :value="ACCOUNT_TYPE.ADMIN">{{ zhCN.session.typeAdmin }}</a-select-option>
        <a-select-option :value="ACCOUNT_TYPE.PORTAL">{{ zhCN.session.typePortal }}</a-select-option>
      </a-select>
      <a-input v-model:value="filters.account" data-testid="filter-account" :placeholder="zhCN.session.account" />
      <a-button type="primary" data-testid="session-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="session-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.session.account">
        <template #default="{ record: row }">{{ row.account }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.session.accountType">
        <template #default="{ record: row }">{{ row.accountType }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.session.tokenLast4">
        <template #default="{ record: row }">{{ row.tokenLast4 }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.session.loginAt">
        <template #default="{ record: row }">{{ formatDateTime(row.loginAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.session.lastActiveAt">
        <template #default="{ record: row }">{{ formatDateTime(row.lastActiveAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.session.ip">
        <template #default="{ record: row }">{{ row.ip }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.session.deviceId">
        <template #default="{ record: row }">{{ row.deviceId || "—" }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.SESSION_KICK" data-testid="session-kick" @click="pendingKick = row">
              {{ zhCN.session.kick }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <ConfirmDialog
      :visible="pendingKick != null"
      :message="zhCN.confirm.kick"
      @confirm="onKickConfirm"
      @cancel="pendingKick = null"
    />
  </section>
</template>
