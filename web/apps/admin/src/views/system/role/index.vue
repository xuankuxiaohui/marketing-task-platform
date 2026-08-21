<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  assignRolePermissions,
  createRole,
  deleteRole,
  fetchPermissionTree,
  pageRoles,
  updateRole,
  type PermissionTreeNode,
  type RoleView,
} from "@/api/identity";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS, SUPER_ADMIN_ROLE } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { flattenPermissionTree } from "@/utils/permission-tree";

defineOptions({ name: "RolePermissionPage" });

const records = ref<RoleView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const formOpen = ref(false);
const assignOpen = ref(false);
const saving = ref(false);
const editing = ref<RoleView | null>(null);
const form = reactive({ code: "", name: "", description: "", status: STATUS.ENABLED as string });
const tree = ref<PermissionTreeNode[]>([]);
const selectedIds = ref<number[]>([]);
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);
const flatPermissions = ref(flattenPermissionTree([]));

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
  editing.value = row;
  const result = await fetchPermissionTree();
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  tree.value = parsed.data ?? [];
  flatPermissions.value = flattenPermissionTree(tree.value);
  selectedIds.value = [];
  assignOpen.value = true;
}

function togglePermission(id: number, checked: boolean): void {
  if (checked) {
    if (!selectedIds.value.includes(id)) {
      selectedIds.value = [...selectedIds.value, id];
    }
    return;
  }
  selectedIds.value = selectedIds.value.filter((item) => item !== id);
}

async function submitForm(): Promise<void> {
  saving.value = true;
  const result: Result = editing.value?.id
    ? await updateRole(editing.value.id, { name: form.name, description: form.description, status: form.status })
    : await createRole({ code: form.code, name: form.name, description: form.description });
  saving.value = false;
  const parsed = okOrFeedback(result);
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
  const result = await assignRolePermissions(editing.value.id, { permissionIds: selectedIds.value });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  assignOpen.value = false;
}

function askDelete(row: RoleView): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteRole(row.id as number);
      const parsed = okOrFeedback(result);
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

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="role-page">
    <h2>{{ zhCN.role.title }}</h2>
    <div class="admin-toolbar">
      <button v-auth="PERMS.ROLE_CREATE" type="button" data-testid="role-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="role-table">
      <thead>
        <tr>
          <th>{{ zhCN.role.code }}</th>
          <th>{{ zhCN.role.name }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.role.userCount }}</th>
          <th>{{ zhCN.common.createdAt }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.status }}</td>
          <td>{{ row.userCount }}</td>
          <td>{{ formatDateTime(row.createdAt) }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.ROLE_UPDATE" type="button" data-testid="role-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button
              v-if="!isBuiltIn(row)"
              v-auth="PERMS.ROLE_ASSIGN"
              type="button"
              data-testid="role-assign"
              @click="openAssign(row)"
            >
              {{ zhCN.role.assign }}
            </button>
            <button
              v-if="!isBuiltIn(row)"
              v-auth="PERMS.ROLE_DELETE"
              type="button"
              data-testid="role-delete"
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
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submitForm"
      @cancel="formOpen = false"
    >
      <label v-if="!editing" class="field">
        <span>{{ zhCN.role.code }}</span>
        <input v-model="form.code" data-testid="role-code" required />
      </label>
      <label class="field">
        <span>{{ zhCN.role.name }}</span>
        <input v-model="form.name" data-testid="role-name" required />
      </label>
      <label class="field">
        <span>{{ zhCN.role.description }}</span>
        <input v-model="form.description" data-testid="role-description" />
      </label>
      <label v-if="editing" class="field">
        <span>{{ zhCN.common.status }}</span>
        <select v-model="form.status" data-testid="role-status">
          <option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</option>
          <option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</option>
        </select>
      </label>
    </FormDialog>
    <FormDialog
      :visible="assignOpen"
      :title="zhCN.role.assign"
      :saving="saving"
      @submit="submitAssign"
      @cancel="assignOpen = false"
    >
      <p class="hint">{{ zhCN.role.assignHint }}</p>
      <label v-for="node in flatPermissions" :key="node.id" class="perm-item" :style="{ paddingLeft: `${node.depth * 16}px` }">
        <input
          type="checkbox"
          :data-testid="`perm-${node.id}`"
          :checked="selectedIds.includes(node.id)"
          @change="togglePermission(node.id, ($event.target as HTMLInputElement).checked)"
        />
        {{ node.name }} <span v-if="node.code" class="hint">{{ node.code }}</span>
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
