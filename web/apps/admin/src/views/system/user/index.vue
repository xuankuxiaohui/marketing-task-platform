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
    <h2>{{ zhCN.user.title }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.username" data-testid="filter-username" :placeholder="zhCN.user.username" />
      <input v-model="filters.nickname" data-testid="filter-nickname" :placeholder="zhCN.user.nickname" />
      <select v-model="filters.status" data-testid="filter-status">
        <option value="">{{ zhCN.common.status }}</option>
        <option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</option>
        <option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</option>
      </select>
      <button type="button" data-testid="user-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.USER_CREATE" type="button" data-testid="user-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="user-table">
      <thead>
        <tr>
          <th>{{ zhCN.user.username }}</th>
          <th>{{ zhCN.user.nickname }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.user.roles }}</th>
          <th>{{ zhCN.user.lastLoginAt }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.username }}</td>
          <td>{{ row.nickname }}</td>
          <td>{{ row.status }}</td>
          <td>{{ (row.roles ?? []).join(", ") }}</td>
          <td>{{ formatDateTime(row.lastLoginAt) }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.USER_UPDATE" type="button" data-testid="user-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button
              v-if="row.status === STATUS.ENABLED && !isProtected(row)"
              v-auth="PERMS.USER_DISABLE"
              type="button"
              data-testid="user-disable"
              @click="askDisable(row)"
            >
              {{ zhCN.common.disable }}
            </button>
            <button
              v-if="row.status === STATUS.DISABLED && !isProtected(row)"
              v-auth="PERMS.USER_DISABLE"
              type="button"
              data-testid="user-enable"
              @click="onEnable(row)"
            >
              {{ zhCN.common.enable }}
            </button>
            <button v-auth="PERMS.USER_RESET" type="button" data-testid="user-reset" @click="openReset(row)">
              {{ zhCN.user.resetPassword }}
            </button>
            <button
              v-if="!isProtected(row)"
              v-auth="PERMS.USER_DELETE"
              type="button"
              data-testid="user-delete"
              @click="askDelete(row)"
            >
              {{ zhCN.common.delete }}
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
    <FormDialog
      :visible="formOpen"
      :title="formMode === 'reset' ? zhCN.user.resetPassword : formMode === 'edit' ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submitForm"
      @cancel="formOpen = false"
    >
      <label v-if="formMode === 'create'" class="field">
        <span>{{ zhCN.user.username }}</span>
        <input v-model="form.username" data-testid="user-username" required />
      </label>
      <label v-if="formMode !== 'reset'" class="field">
        <span>{{ zhCN.user.nickname }}</span>
        <input v-model="form.nickname" data-testid="user-nickname" required />
      </label>
      <label v-if="formMode !== 'edit'" class="field">
        <span>{{ zhCN.user.password }}</span>
        <input v-model="form.password" data-testid="user-password" type="password" required />
        <span class="hint">{{ zhCN.common.passwordPolicy }}</span>
      </label>
      <fieldset v-if="formMode !== 'reset'" class="field">
        <legend>{{ zhCN.user.roles }}</legend>
        <label v-for="role in roles" :key="role.id">
          <input
            type="checkbox"
            :checked="form.roleIds.includes(Number(role.id))"
            @change="toggleRole(Number(role.id), ($event.target as HTMLInputElement).checked)"
          />
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
