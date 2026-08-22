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
    <div class="admin-page__header">
      <h2>{{ zhCN.portal.title }}</h2>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.username" data-testid="filter-username" :placeholder="zhCN.user.username" />
      <el-input v-model="filters.nickname" data-testid="filter-nickname" :placeholder="zhCN.user.nickname" />
      <el-input v-model="filters.province" data-testid="filter-province" :placeholder="zhCN.portal.province" />
      <el-select v-model="filters.status" data-testid="filter-status">
        <el-option value="" :label="zhCN.common.status" />
        <el-option :value="STATUS.ENABLED" :label="zhCN.common.enabled" />
        <el-option :value="STATUS.DISABLED" :label="zhCN.common.disabled" />
      </el-select>
      <el-button data-testid="portal-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="portal-user-table" size="small" stripe>
      <el-table-column :label="zhCN.user.username">
        <template #default="{ row }">{{ row.username }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.user.nickname">
        <template #default="{ row }">{{ row.nickname }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.portal.province">
        <template #default="{ row }">{{ row.province || "—" }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.portal.level">
        <template #default="{ row }">{{ row.userLevel || "—" }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'info'"
            :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'"
          >
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="zhCN.portal.registeredAt">
        <template #default="{ row }">{{ formatDateTime(row.registeredAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.PORTAL_QUERY" data-testid="portal-detail" @click="openDetail(row)">
              {{ zhCN.portal.detail }}
            </el-button>
            <el-button text v-auth="PERMS.PORTAL_PROFILE" data-testid="portal-profile" @click="openProfile(row)">
              {{ zhCN.portal.profile }}
            </el-button>
            <el-button text
              v-if="row.status === STATUS.ENABLED"
              v-auth="PERMS.PORTAL_DISABLE"
              data-testid="portal-disable"
              @click="askDisable(row)"
            >
              {{ zhCN.common.disable }}
            </el-button>
            <el-button text
              v-if="row.status === STATUS.DISABLED"
              v-auth="PERMS.PORTAL_DISABLE"
              data-testid="portal-enable"
              @click="onEnable(row)"
            >
              {{ zhCN.common.enable }}
            </el-button>
            <el-button text v-auth="PERMS.PORTAL_RESET" data-testid="portal-reset" @click="openReset(row)">
              {{ zhCN.user.resetPassword }}
            </el-button>
            <el-button text v-auth="PERMS.PORTAL_DELETE" data-testid="portal-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
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
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.page }} -</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.page }} +</el-button>
    </div>
    <FormDialog :visible="profileOpen" :title="zhCN.portal.profile" :saving="saving" @submit="submitProfile" @cancel="profileOpen = false">
      <el-form-item :label="zhCN.portal.province">
        <el-input v-model="profile.province" data-testid="profile-province" />
      </el-form-item>
      <el-form-item :label="zhCN.portal.level">
        <el-input v-model="profile.userLevel" data-testid="profile-level" />
      </el-form-item>
      <el-form-item :label="zhCN.portal.userRole">
        <el-input v-model="profile.userRole" data-testid="profile-role" />
      </el-form-item>
      <el-form-item :label="zhCN.portal.tag">
        <el-input v-model="profile.tags" data-testid="profile-tags" />
      </el-form-item>
      <el-form-item :label="zhCN.portal.orgId">
        <el-input v-model="profile.orgId" data-testid="profile-org" />
      </el-form-item>
    </FormDialog>
    <FormDialog :visible="resetOpen" :title="zhCN.user.resetPassword" :saving="saving" @submit="submitReset" @cancel="resetOpen = false">
      <el-form-item :label="zhCN.user.newPassword">
        <el-input v-model="newPassword" data-testid="portal-new-password" type="password" required />
      </el-form-item>
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
