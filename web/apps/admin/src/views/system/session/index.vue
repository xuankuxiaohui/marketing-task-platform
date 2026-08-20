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
    <div class="admin-toolbar">
      <select v-model="filters.accountType" data-testid="filter-account-type">
        <option value="">{{ zhCN.session.accountType }}</option>
        <option :value="ACCOUNT_TYPE.ADMIN">{{ zhCN.session.typeAdmin }}</option>
        <option :value="ACCOUNT_TYPE.PORTAL">{{ zhCN.session.typePortal }}</option>
      </select>
      <input v-model="filters.account" data-testid="filter-account" :placeholder="zhCN.session.account" />
      <button type="button" data-testid="session-query" @click="load">{{ zhCN.common.query }}</button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="session-table">
      <thead>
        <tr>
          <th>{{ zhCN.session.account }}</th>
          <th>{{ zhCN.session.accountType }}</th>
          <th>{{ zhCN.session.tokenLast4 }}</th>
          <th>{{ zhCN.session.loginAt }}</th>
          <th>{{ zhCN.session.lastActiveAt }}</th>
          <th>{{ zhCN.session.ip }}</th>
          <th>{{ zhCN.session.deviceId }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in records" :key="`${row.account}-${row.tokenLast4}-${index}`">
          <td>{{ row.account }}</td>
          <td>{{ row.accountType }}</td>
          <td>{{ row.tokenLast4 }}</td>
          <td>{{ formatDateTime(row.loginAt) }}</td>
          <td>{{ formatDateTime(row.lastActiveAt) }}</td>
          <td>{{ row.ip }}</td>
          <td>{{ row.deviceId || "—" }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.SESSION_KICK" type="button" data-testid="session-kick" @click="pendingKick = row">
              {{ zhCN.session.kick }}
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
    <ConfirmDialog
      :visible="pendingKick != null"
      :message="zhCN.confirm.kick"
      @confirm="onKickConfirm"
      @cancel="pendingKick = null"
    />
  </section>
</template>
