<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  deletePortalUser,
  disablePortalUser,
  enablePortalUser,
  fetchPortalUser,
  pagePortalUsers,
  resetPortalPassword,
  updatePortalProfile,
  type PortalUserDetail,
  type PortalUserView,
} from "@/api/identity";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "PortalUserPage" });

const records = ref<PortalUserView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({
  username: "",
  nickname: "",
  province: "",
  level: "",
  tag: "",
  status: "",
});
const detail = ref<PortalUserDetail | null>(null);
const profileOpen = ref(false);
const resetOpen = ref(false);
const saving = ref(false);
const editingId = ref<number | null>(null);
const profile = reactive({
  province: "",
  userLevel: "",
  userRole: "",
  tags: "",
  orgId: "",
});
const newPassword = ref("");
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pagePortalUsers({
    ...filters,
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

async function openDetail(row: PortalUserView): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await fetchPortalUser(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  detail.value = parsed.data ?? null;
}

function openProfile(row: PortalUserView): void {
  editingId.value = row.id ?? null;
  profile.province = row.province ?? "";
  profile.userLevel = row.userLevel ?? "";
  profile.userRole = row.userRole ?? "";
  profile.tags = (row.tags ?? []).join(",");
  profile.orgId = row.orgId ?? "";
  profileOpen.value = true;
}

function openReset(row: PortalUserView): void {
  editingId.value = row.id ?? null;
  newPassword.value = "";
  resetOpen.value = true;
}

async function submitProfile(): Promise<void> {
  if (editingId.value == null) {
    return;
  }
  saving.value = true;
  const tags = profile.tags
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const result = await updatePortalProfile(editingId.value, {
    province: profile.province || undefined,
    userLevel: profile.userLevel || undefined,
    userRole: profile.userRole || undefined,
    tags,
    orgId: profile.orgId || undefined,
  });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  profileOpen.value = false;
  await load();
}

async function submitReset(): Promise<void> {
  if (editingId.value == null) {
    return;
  }
  saving.value = true;
  const result = await resetPortalPassword(editingId.value, newPassword.value);
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  resetOpen.value = false;
}

function askDisable(row: PortalUserView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.disable,
    run: async () => {
      const result = await disablePortalUser(row.id as number);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

function askDelete(row: PortalUserView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deletePortalUser(row.id as number);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

async function onEnable(row: PortalUserView): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await enablePortalUser(row.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

async function onConfirm(): Promise<void> {
  const current = confirm.value;
  confirm.value = null;
  await current?.run();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="portal-user-page">
    <h2>{{ zhCN.portal.title }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.username" data-testid="filter-username" :placeholder="zhCN.user.username" />
      <input v-model="filters.nickname" data-testid="filter-nickname" :placeholder="zhCN.user.nickname" />
      <input v-model="filters.province" data-testid="filter-province" :placeholder="zhCN.portal.province" />
      <select v-model="filters.status" data-testid="filter-status">
        <option value="">{{ zhCN.common.status }}</option>
        <option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</option>
        <option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</option>
      </select>
      <button type="button" data-testid="portal-query" @click="load">{{ zhCN.common.query }}</button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="portal-user-table">
      <thead>
        <tr>
          <th>{{ zhCN.user.username }}</th>
          <th>{{ zhCN.user.nickname }}</th>
          <th>{{ zhCN.portal.province }}</th>
          <th>{{ zhCN.portal.level }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.portal.registeredAt }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.username }}</td>
          <td>{{ row.nickname }}</td>
          <td>{{ row.province || "—" }}</td>
          <td>{{ row.userLevel || "—" }}</td>
          <td>{{ row.status }}</td>
          <td>{{ formatDateTime(row.registeredAt) }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.PORTAL_QUERY" type="button" data-testid="portal-detail" @click="openDetail(row)">
              {{ zhCN.portal.detail }}
            </button>
            <button v-auth="PERMS.PORTAL_PROFILE" type="button" data-testid="portal-profile" @click="openProfile(row)">
              {{ zhCN.portal.profile }}
            </button>
            <button
              v-if="row.status === STATUS.ENABLED"
              v-auth="PERMS.PORTAL_DISABLE"
              type="button"
              data-testid="portal-disable"
              @click="askDisable(row)"
            >
              {{ zhCN.common.disable }}
            </button>
            <button
              v-if="row.status === STATUS.DISABLED"
              v-auth="PERMS.PORTAL_DISABLE"
              type="button"
              data-testid="portal-enable"
              @click="onEnable(row)"
            >
              {{ zhCN.common.enable }}
            </button>
            <button v-auth="PERMS.PORTAL_RESET" type="button" data-testid="portal-reset" @click="openReset(row)">
              {{ zhCN.user.resetPassword }}
            </button>
            <button v-auth="PERMS.PORTAL_DELETE" type="button" data-testid="portal-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="detail" class="detail-card" data-testid="portal-detail-card">
      <h3>{{ zhCN.portal.detail }} · {{ detail.username }}</h3>
      <p>{{ zhCN.portal.inProgress }}：{{ detail.inProgressInstanceCount }}</p>
      <p>{{ zhCN.portal.history }}：{{ detail.historyInstanceCount }}</p>
      <p>{{ zhCN.portal.points }}：{{ detail.pointsBalance }}</p>
      <p>{{ zhCN.portal.prizes }}：won {{ detail.prizeSummary?.won ?? 0 }} / granted {{ detail.prizeSummary?.granted ?? 0 }}</p>
      <p>{{ zhCN.portal.riskHits }}：{{ detail.riskHits }}</p>
      <p>{{ zhCN.portal.listStatus }}：{{ (detail.listStatus ?? []).join(", ") || "—" }}</p>
    </div>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <button type="button" :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</button>
      <span>{{ page }}</span>
      <button type="button" :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</button>
    </div>
    <FormDialog :visible="profileOpen" :title="zhCN.portal.profile" :saving="saving" @submit="submitProfile" @cancel="profileOpen = false">
      <label class="field">
        <span>{{ zhCN.portal.province }}</span>
        <input v-model="profile.province" data-testid="profile-province" />
      </label>
      <label class="field">
        <span>{{ zhCN.portal.level }}</span>
        <input v-model="profile.userLevel" data-testid="profile-level" />
      </label>
      <label class="field">
        <span>{{ zhCN.portal.userRole }}</span>
        <input v-model="profile.userRole" data-testid="profile-role" />
      </label>
      <label class="field">
        <span>{{ zhCN.portal.tag }}</span>
        <input v-model="profile.tags" data-testid="profile-tags" />
      </label>
      <label class="field">
        <span>{{ zhCN.portal.orgId }}</span>
        <input v-model="profile.orgId" data-testid="profile-org" />
      </label>
    </FormDialog>
    <FormDialog :visible="resetOpen" :title="zhCN.user.resetPassword" :saving="saving" @submit="submitReset" @cancel="resetOpen = false">
      <label class="field">
        <span>{{ zhCN.user.newPassword }}</span>
        <input v-model="newPassword" data-testid="portal-new-password" type="password" required />
      </label>
    </FormDialog>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>

<style scoped>
.detail-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
}
</style>
