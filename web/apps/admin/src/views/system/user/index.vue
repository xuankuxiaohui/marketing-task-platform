<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  createUser,
  deleteUser,
  disableUser,
  enableUser,
  pageRoles,
  pageUsers,
  resetUserPassword,
  updateUser,
  type AdminUserView,
  type RoleView,
} from "@/api/identity";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS, SUPER_ADMIN_ROLE } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { useSessionStore } from "@/store/session";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "AdminUserPage" });

const session = useSessionStore();
const records = ref<AdminUserView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ username: "", nickname: "", status: "", roleId: "" });
const roles = ref<RoleView[]>([]);
const formOpen = ref(false);
const formMode = ref<"create" | "edit" | "reset">("create");
const saving = ref(false);
const editing = ref<AdminUserView | null>(null);
const form = reactive({
  username: "",
  nickname: "",
  password: "",
  roleIds: [] as number[],
});
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

function isProtected(row: AdminUserView): boolean {
  return row.id === session.userId || (row.roles ?? []).includes(SUPER_ADMIN_ROLE);
}

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageUsers({
    username: filters.username,
    nickname: filters.nickname,
    status: filters.status,
    roleId: filters.roleId ? Number(filters.roleId) : undefined,
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

async function loadRoles(): Promise<void> {
  const result = await pageRoles({ all: true, page: 1, pageSize: 100 });
  const parsed = okOrFeedback(result);
  if (parsed.ok) {
    roles.value = parsed.data?.records ?? [];
  }
}

function openCreate(): void {
  formMode.value = "create";
  editing.value = null;
  form.username = "";
  form.nickname = "";
  form.password = "";
  form.roleIds = [];
  formOpen.value = true;
}

function openEdit(row: AdminUserView): void {
  formMode.value = "edit";
  editing.value = row;
  form.username = row.username ?? "";
  form.nickname = row.nickname ?? "";
  form.password = "";
  form.roleIds = roles.value.filter((role) => (row.roles ?? []).includes(role.code ?? "")).map((role) => Number(role.id));
  formOpen.value = true;
}

function openReset(row: AdminUserView): void {
  formMode.value = "reset";
  editing.value = row;
  form.password = "";
  formOpen.value = true;
}

function toggleRole(id: number, checked: boolean): void {
  if (checked) {
    form.roleIds = [...form.roleIds, id];
    return;
  }
  form.roleIds = form.roleIds.filter((item) => item !== id);
}

async function submitForm(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  let result: Result | undefined;
  if (formMode.value === "create") {
    result = await createUser({
      username: form.username,
      nickname: form.nickname,
      password: form.password,
      roleIds: form.roleIds,
    });
  } else if (formMode.value === "edit" && editing.value?.id != null) {
    result = await updateUser(editing.value.id, { nickname: form.nickname, roleIds: form.roleIds });
  } else if (formMode.value === "reset" && editing.value?.id != null) {
    result = await resetUserPassword(editing.value.id, form.password);
  }
  saving.value = false;
  if (!result) {
    return;
  }
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

function askDisable(row: AdminUserView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.disable,
    run: async () => {
      const result = await disableUser(row.id as number);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

function askDelete(row: AdminUserView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteUser(row.id as number);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

async function onEnable(row: AdminUserView): Promise<void> {
  if (row.id == null) {
    return;
  }
  const result = await enableUser(row.id);
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

onMounted(async () => {
  await loadRoles();
  await load();
});
</script>

<template>
  <section class="admin-page" data-testid="user-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.user.title }}</h2>
      <el-button type="primary" v-auth="PERMS.USER_CREATE" data-testid="user-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.username" data-testid="filter-username" :placeholder="zhCN.user.username" />
      <el-input v-model="filters.nickname" data-testid="filter-nickname" :placeholder="zhCN.user.nickname" />
      <el-select v-model="filters.status" data-testid="filter-status">
        <el-option value="" :label="zhCN.common.status" />
        <el-option :value="STATUS.ENABLED" :label="zhCN.common.enabled" />
        <el-option :value="STATUS.DISABLED" :label="zhCN.common.disabled" />
      </el-select>
      <el-button data-testid="user-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.USER_CREATE" text type="primary" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="user-table" size="small" stripe>
      <el-table-column :label="zhCN.user.username">
        <template #default="{ row }">{{ row.username }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.user.nickname">
        <template #default="{ row }">{{ row.nickname }}</template>
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
      <el-table-column :label="zhCN.user.roles">
        <template #default="{ row }">{{ (row.roles ?? []).join(", ") }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.user.lastLoginAt">
        <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.USER_UPDATE" data-testid="user-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button text
              v-if="row.status === STATUS.ENABLED && !isProtected(row)"
              v-auth="PERMS.USER_DISABLE"
              data-testid="user-disable"
              @click="askDisable(row)"
            >
              {{ zhCN.common.disable }}
            </el-button>
            <el-button text
              v-if="row.status === STATUS.DISABLED && !isProtected(row)"
              v-auth="PERMS.USER_DISABLE"
              data-testid="user-enable"
              @click="onEnable(row)"
            >
              {{ zhCN.common.enable }}
            </el-button>
            <el-button text v-auth="PERMS.USER_RESET" data-testid="user-reset" @click="openReset(row)">
              {{ zhCN.user.resetPassword }}
            </el-button>
            <el-button text
              v-if="!isProtected(row)"
              v-auth="PERMS.USER_DELETE"
              data-testid="user-delete"
              @click="askDelete(row)"
            >
              {{ zhCN.common.delete }}
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
    <FormDialog
      :visible="formOpen"
      :title="formMode === 'reset' ? zhCN.user.resetPassword : formMode === 'edit' ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submitForm"
      @cancel="formOpen = false"
    >
      <el-form-item v-if="formMode === 'create'" :label="zhCN.user.username">
        <el-input v-model="form.username" data-testid="user-username" required />
      </el-form-item>
      <el-form-item v-if="formMode !== 'reset'" :label="zhCN.user.nickname">
        <el-input v-model="form.nickname" data-testid="user-nickname" required />
      </el-form-item>
      <el-form-item v-if="formMode !== 'edit'" :label="zhCN.user.password">
        <el-input v-model="form.password" data-testid="user-password" type="password" required />
        <span class="hint">{{ zhCN.common.passwordPolicy }}</span>
      </el-form-item>
      <fieldset v-if="formMode !== 'reset'" class="field">
        <legend>{{ zhCN.user.roles }}</legend>
        <label v-for="role in roles" :key="role.id">
          <el-checkbox
            :model-value="form.roleIds.includes(Number(role.id))"
            @update:model-value="(val: boolean) => toggleRole(Number(role.id), val)" />
          {{ role.name }}
        </label>
      </fieldset>
    </FormDialog>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>
