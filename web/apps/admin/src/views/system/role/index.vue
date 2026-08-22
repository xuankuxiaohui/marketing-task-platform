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
    <div class="admin-page__header">
      <h2>{{ zhCN.role.title }}</h2>
      <el-button type="primary" v-auth="PERMS.ROLE_CREATE" data-testid="role-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.ROLE_CREATE" text type="primary" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="role-table" size="small" stripe>
      <el-table-column :label="zhCN.role.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.role.name">
        <template #default="{ row }">{{ row.name }}</template>
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
      <el-table-column :label="zhCN.role.userCount">
        <template #default="{ row }">{{ row.userCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.createdAt">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.ROLE_UPDATE" data-testid="role-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button text
              v-if="!isBuiltIn(row)"
              v-auth="PERMS.ROLE_ASSIGN"
              data-testid="role-assign"
              @click="openAssign(row)"
            >
              {{ zhCN.role.assign }}
            </el-button>
            <el-button text
              v-if="!isBuiltIn(row)"
              v-auth="PERMS.ROLE_DELETE"
              data-testid="role-delete"
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
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submitForm"
      @cancel="formOpen = false"
    >
      <el-form-item v-if="!editing" :label="zhCN.role.code">
        <el-input v-model="form.code" data-testid="role-code" required />
      </el-form-item>
      <el-form-item :label="zhCN.role.name">
        <el-input v-model="form.name" data-testid="role-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.role.description">
        <el-input v-model="form.description" data-testid="role-description" />
      </el-form-item>
      <el-form-item v-if="editing" :label="zhCN.common.status">
        <el-select v-model="form.status" data-testid="role-status">
        <el-option :value="STATUS.ENABLED" :label="zhCN.common.enabled" />
        <el-option :value="STATUS.DISABLED" :label="zhCN.common.disabled" />
      </el-select>
      </el-form-item>
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
        <el-checkbox
          :data-testid="`perm-${node.id}`"
          :model-value="selectedIds.includes(node.id)"
          @update:model-value="(val: boolean) => togglePermission(node.id, val)" />
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
