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
import { adminStatusLabel } from "@/utils/status-label";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "PortalUserPage" });

const records = ref<PortalUserView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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
  const parsed = writeOrFeedback(result);
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
  const parsed = writeOrFeedback(result);
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
      const parsed = writeOrFeedback(result);
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
      const parsed = writeOrFeedback(result);
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
  const parsed = writeOrFeedback(result);
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="portal-user-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.portal.title }}</h2>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.username" data-testid="filter-username" :placeholder="zhCN.user.username" />
      <a-input v-model:value="filters.nickname" data-testid="filter-nickname" :placeholder="zhCN.user.nickname" />
      <a-input v-model:value="filters.province" data-testid="filter-province" :placeholder="zhCN.portal.province" />
      <a-select v-model:value="filters.status" data-testid="filter-status">
        <a-select-option value="">{{ zhCN.common.status }}</a-select-option>
        <a-select-option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</a-select-option>
        <a-select-option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</a-select-option>
      </a-select>
      <a-button type="primary" data-testid="portal-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="portal-user-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.user.username">
        <template #default="{ record: row }">{{ row.username }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.user.nickname">
        <template #default="{ record: row }">{{ row.nickname }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.portal.province">
        <template #default="{ record: row }">{{ row.province || "—" }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.portal.level">
        <template #default="{ record: row }">{{ row.userLevel || "—" }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.portal.registeredAt">
        <template #default="{ record: row }">{{ formatDateTime(row.registeredAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.PORTAL_QUERY" data-testid="portal-detail" @click="openDetail(row)">
              {{ zhCN.portal.detail }}
            </a-button>
            <a-button size="small" v-auth="PERMS.PORTAL_PROFILE" data-testid="portal-profile" @click="openProfile(row)">
              {{ zhCN.portal.profile }}
            </a-button>
            <a-button size="small" v-if="row.status === STATUS.ENABLED" v-auth="PERMS.PORTAL_DISABLE" data-testid="portal-disable" @click="askDisable(row)">
              {{ zhCN.common.disable }}
            </a-button>
            <a-button size="small" v-if="row.status === STATUS.DISABLED" v-auth="PERMS.PORTAL_DISABLE" data-testid="portal-enable" @click="onEnable(row)">
              {{ zhCN.common.enable }}
            </a-button>
            <a-button size="small" v-auth="PERMS.PORTAL_RESET" data-testid="portal-reset" @click="openReset(row)">
              {{ zhCN.user.resetPassword }}
            </a-button>
            <a-button size="small" danger v-auth="PERMS.PORTAL_DELETE" data-testid="portal-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <div v-if="detail" class="detail-card" data-testid="portal-detail-card">
      <h3>{{ zhCN.portal.detail }} · {{ detail.username }}</h3>
      <p>{{ zhCN.portal.inProgress }}：{{ detail.inProgressInstanceCount }}</p>
      <p>{{ zhCN.portal.history }}：{{ detail.historyInstanceCount }}</p>
      <p>{{ zhCN.portal.points }}：{{ detail.pointsBalance }}</p>
      <p>{{ zhCN.portal.prizes }}：won {{ detail.prizeSummary?.won ?? 0 }} / granted {{ detail.prizeSummary?.granted ?? 0 }}</p>
      <p>{{ zhCN.portal.riskHits }}：{{ detail.riskHits }}</p>
      <p>{{ zhCN.portal.listStatus }}：{{ (detail.listStatus ?? []).join(", ") || "—" }}</p>
    </div>
    <FormDialog :visible="profileOpen" :title="zhCN.portal.profile" :saving="saving" @submit="submitProfile" @cancel="profileOpen = false">
      <a-form-item :label="zhCN.portal.province">
        <a-input v-model:value="profile.province" data-testid="profile-province" />
      </a-form-item>
      <a-form-item :label="zhCN.portal.level">
        <a-input v-model:value="profile.userLevel" data-testid="profile-level" />
      </a-form-item>
      <a-form-item :label="zhCN.portal.userRole">
        <a-input v-model:value="profile.userRole" data-testid="profile-role" />
      </a-form-item>
      <a-form-item :label="zhCN.portal.tag">
        <a-input v-model:value="profile.tags" data-testid="profile-tags" />
      </a-form-item>
      <a-form-item :label="zhCN.portal.orgId">
        <a-input v-model:value="profile.orgId" data-testid="profile-org" />
      </a-form-item>
    </FormDialog>
    <FormDialog :visible="resetOpen" :title="zhCN.user.resetPassword" :saving="saving" @submit="submitReset" @cancel="resetOpen = false">
      <a-form-item :label="zhCN.user.newPassword">
        <a-input-password v-model:value="newPassword" data-testid="portal-new-password" required />
      </a-form-item>
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
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  padding: 12px 16px;
}
</style>
