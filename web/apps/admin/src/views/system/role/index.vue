<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  assignRolePermissions,
  createRole,
  deleteRole,
  fetchPermissionTree,
  fetchRolePermissions,
  pageRoles,
  pageUsers,
  updateRole,
  updateUser,
  type AdminUserView,
  type PermissionTreeNode,
  type RoleView,
} from "@/api/identity";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS, SUPER_ADMIN_ROLE } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { toPermissionTreeData } from "@/utils/permission-tree";
import { remainingRoleIdsAfterRevoke, toCheckedPermissionIds } from "@/utils/role-assign";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "RolePermissionPage" });

const records = ref<RoleView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const formOpen = ref(false);
const assignOpen = ref(false);
const saving = ref(false);
const editing = ref<RoleView | null>(null);
const form = reactive({ code: "", name: "", description: "", status: STATUS.ENABLED as string });
const tree = ref<PermissionTreeNode[]>([]);
const selectedIds = ref<number[]>([]);
const holders = ref<AdminUserView[]>([]);
const roleCatalog = ref<RoleView[]>([]);
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);
const treeData = ref(toPermissionTreeData([]));

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageRoles({ page: page.value, pageSize });
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  records.value = parsed.data?.records ?? [];
  total.value = parsed.data?.total ?? 0;
}

function isBuiltIn(row: RoleView): boolean {
  return row.code === SUPER_ADMIN_ROLE;
}

function openCreate(): void {
  editing.value = null;
  form.code = "";
  form.name = "";
  form.description = "";
  form.status = STATUS.ENABLED;
  formOpen.value = true;
}

function openEdit(row: RoleView): void {
  editing.value = row;
  form.code = row.code ?? "";
  form.name = row.name ?? "";
  form.description = "";
  form.status = row.status ?? STATUS.ENABLED;
  formOpen.value = true;
}

async function openAssign(row: RoleView): Promise<void> {
  if (row.id == null) {
    return;
  }
  editing.value = row;
  feedback.value = null;
  const [treeResult, permResult, usersResult, rolesResult] = await Promise.all([
    fetchPermissionTree(),
    fetchRolePermissions(row.id),
    pageUsers({ roleId: row.id, page: 1, pageSize: 100 }),
    pageRoles({ all: true, page: 1, pageSize: 100 }),
  ]);
  const treeParsed = okOrFeedback(treeResult);
  if (!treeParsed.ok) {
    feedback.value = treeParsed.feedback;
    return;
  }
  const permParsed = okOrFeedback(permResult);
  if (!permParsed.ok) {
    feedback.value = permParsed.feedback;
    return;
  }
  tree.value = treeParsed.data ?? [];
  treeData.value = toPermissionTreeData(tree.value);
  selectedIds.value = toCheckedPermissionIds(permParsed.data?.permissionIds);
  const usersParsed = okOrFeedback(usersResult);
  holders.value = usersParsed.ok ? (usersParsed.data?.records ?? []) : [];
  if (!usersParsed.ok) {
    feedback.value = usersParsed.feedback;
  }
  const rolesParsed = okOrFeedback(rolesResult);
  roleCatalog.value = rolesParsed.ok ? (rolesParsed.data?.records ?? []) : [];
  assignOpen.value = true;
}

function onPermCheck(keys: (string | number)[] | { checked: (string | number)[] }): void {
  const list = Array.isArray(keys) ? keys : keys.checked;
  selectedIds.value = list.map(Number);
}

async function submitForm(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result: Result = editing.value?.id
    ? await updateRole(editing.value.id, { name: form.name, description: form.description, status: form.status })
    : await createRole({ code: form.code, name: form.name, description: form.description });
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function submitAssign(): Promise<void> {
  if (editing.value?.id == null) {
    return;
  }
  saving.value = true;
  feedback.value = null;
  const result = await assignRolePermissions(editing.value.id, {
    permissionIds: toCheckedPermissionIds(selectedIds.value),
  });
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  assignOpen.value = false;
}

function askRevoke(user: AdminUserView): void {
  if (user.id == null || editing.value?.id == null) {
    return;
  }
  const roleId = editing.value.id;
  confirm.value = {
    message: zhCN.role.revokeConfirm,
    run: async () => {
      const result = await updateUser(user.id as number, {
        nickname: user.nickname ?? "",
        roleIds: remainingRoleIdsAfterRevoke(roleCatalog.value, user.roles, roleId),
      });
      const parsed = writeOrFeedback(result, zhCN.role.revoked);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      holders.value = holders.value.filter((row) => row.id !== user.id);
      await load();
    },
  };
}

function askDelete(row: RoleView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteRole(row.id as number);
      const parsed = writeOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
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
  <section class="admin-page" data-testid="role-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.role.title }}</h2>
      <a-button type="primary" v-auth="PERMS.ROLE_CREATE" data-testid="role-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="role-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.ROLE_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.role.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.role.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.role.userCount">
        <template #default="{ record: row }">{{ row.userCount }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.createdAt">
        <template #default="{ record: row }">{{ formatDateTime(row.createdAt) }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.ROLE_UPDATE" data-testid="role-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" v-if="!isBuiltIn(row)" v-auth="PERMS.ROLE_ASSIGN" data-testid="role-assign" @click="openAssign(row)">
              {{ zhCN.role.assign }}
            </a-button>
            <a-button size="small" danger v-if="!isBuiltIn(row)" v-auth="PERMS.ROLE_DELETE" data-testid="role-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      :feedback="formOpen ? feedback : null"
      @submit="submitForm"
      @cancel="formOpen = false"
    >
      <a-form-item v-if="!editing" :label="zhCN.role.code">
        <a-input v-model:value="form.code" data-testid="role-code" required />
      </a-form-item>
      <a-form-item :label="zhCN.role.name">
        <a-input v-model:value="form.name" data-testid="role-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.role.description">
        <a-input v-model:value="form.description" data-testid="role-description" />
      </a-form-item>
      <a-form-item v-if="editing" :label="zhCN.common.status">
        <a-select v-model:value="form.status" data-testid="role-status">
        <a-select-option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</a-select-option>
        <a-select-option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</a-select-option>
      </a-select>
      </a-form-item>
    </FormDialog>
    <FormDialog
      :visible="assignOpen"
      :title="zhCN.role.assign"
      :saving="saving"
      :feedback="assignOpen ? feedback : null"
      @submit="submitAssign"
      @cancel="assignOpen = false"
    >
      <p class="hint">{{ zhCN.role.assignHint }}</p>
      <div class="perm-tree-wrap">
        <a-tree
          v-if="treeData.length > 0"
          class="perm-tree"
          checkable
          block-node
          default-expand-all
          :selectable="false"
          :tree-data="treeData"
          :checked-keys="selectedIds"
          @check="onPermCheck"
        >
          <template #title="{ title, key }">
            <span :data-testid="`perm-${key}`">{{ title }}</span>
          </template>
        </a-tree>
      </div>
      <div class="role-holders" data-testid="role-holders">
        <h3>{{ zhCN.role.holders }}</h3>
        <p v-if="holders.length === 0" class="hint">{{ zhCN.role.noHolders }}</p>
        <ul v-else>
          <li v-for="user in holders" :key="user.id" class="role-holders__row" :data-testid="`role-holder-${user.id}`">
            <span>{{ user.username }}（{{ user.nickname }}）</span>
            <a-button
              size="small"
              danger
              v-auth="PERMS.USER_UPDATE"
              data-testid="role-revoke"
              @click="askRevoke(user)"
            >
              {{ zhCN.role.revoke }}
            </a-button>
          </li>
        </ul>
      </div>
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
.perm-tree-wrap {
  grid-column: 1 / -1;
  max-height: 420px;
  overflow: auto;
  padding: 8px 12px;
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: #fff;
}
.perm-tree {
  background: transparent;
}
.role-holders {
  grid-column: 1 / -1;
}
.role-holders h3 {
  margin: 8px 0 8px;
  font-size: 14px;
  font-weight: 600;
}
.role-holders ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.role-holders__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: #fff;
}
</style>
