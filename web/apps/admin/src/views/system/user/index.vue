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
import { adminStatusLabel } from "@/utils/status-label";
import { useSessionStore } from "@/store/session";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "AdminUserPage" });

const session = useSessionStore();
const records = ref<AdminUserView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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
  const parsed = writeOrFeedback(result);
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
      const parsed = writeOrFeedback(result);
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
      const parsed = writeOrFeedback(result);
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

onMounted(async () => {
  await loadRoles();
  await load();
});

function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}
</script>

<template>
  <section class="admin-page" data-testid="user-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.user.title }}</h2>
      <a-button type="primary" v-auth="PERMS.USER_CREATE" data-testid="user-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.username" data-testid="filter-username" :placeholder="zhCN.user.username" />
      <a-input v-model:value="filters.nickname" data-testid="filter-nickname" :placeholder="zhCN.user.nickname" />
      <a-select v-model:value="filters.status" data-testid="filter-status">
        <a-select-option value="">{{ zhCN.common.status }}</a-select-option>
        <a-select-option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</a-select-option>
        <a-select-option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</a-select-option>
      </a-select>
      <a-button type="primary" data-testid="user-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="user-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.USER_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.user.username">
        <template #default="{ record: row }">{{ row.username }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.user.nickname">
        <template #default="{ record: row }">{{ row.nickname }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.user.roles">
        <template #default="{ record: row }">{{ (row.roles ?? []).join(", ") }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.user.lastLoginAt">
        <template #default="{ record: row }">{{ formatDateTime(row.lastLoginAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.USER_UPDATE" data-testid="user-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" v-if="row.status === STATUS.ENABLED && !isProtected(row)" v-auth="PERMS.USER_DISABLE" data-testid="user-disable" @click="askDisable(row)">
              {{ zhCN.common.disable }}
            </a-button>
            <a-button size="small" v-if="row.status === STATUS.DISABLED && !isProtected(row)" v-auth="PERMS.USER_DISABLE" data-testid="user-enable" @click="onEnable(row)">
              {{ zhCN.common.enable }}
            </a-button>
            <a-button size="small" v-auth="PERMS.USER_RESET" data-testid="user-reset" @click="openReset(row)">
              {{ zhCN.user.resetPassword }}
            </a-button>
            <a-button size="small" danger v-if="!isProtected(row)" v-auth="PERMS.USER_DELETE" data-testid="user-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog
      :visible="formOpen"
      :title="formMode === 'reset' ? zhCN.user.resetPassword : formMode === 'edit' ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      :feedback="formOpen ? feedback : null"
      @submit="submitForm"
      @cancel="formOpen = false"
    >
      <a-form-item v-if="formMode === 'create'" :label="zhCN.user.username">
        <a-input v-model:value="form.username" data-testid="user-username" required />
      </a-form-item>
      <a-form-item v-if="formMode !== 'reset'" :label="zhCN.user.nickname">
        <a-input v-model:value="form.nickname" data-testid="user-nickname" required />
      </a-form-item>
      <a-form-item v-if="formMode !== 'edit'" :label="zhCN.user.password">
        <a-input-password v-model:value="form.password" data-testid="user-password" required />
        <span class="hint">{{ zhCN.common.passwordPolicy }}</span>
      </a-form-item>
      <fieldset v-if="formMode !== 'reset'" class="field">
        <legend>{{ zhCN.user.roles }}</legend>
        <label v-for="role in roles" :key="role.id">
          <a-checkbox :checked="form.roleIds.includes(Number(role.id))" @update:checked="(val: boolean) => toggleRole(Number(role.id), val)" />
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
