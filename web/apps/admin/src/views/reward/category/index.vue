<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  createCategory,
  deleteCategory,
  disableCategory,
  enableCategory,
  pageCategories,
  updateCategory,
  type PrizeCategoryResponse,
} from "@/api/reward";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS, STATUS } from "@/constants/identity";
import { COST_MODES, FULFILLMENT_MODES, RECON_POLICIES, REWARD_TARGETS } from "@/constants/reward";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "RewardCategoryPage" });

const records = ref<PrizeCategoryResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<PrizeCategoryResponse | null>(null);
const form = reactive({
  code: "",
  name: "",
  rewardTarget: "PLATFORM",
  fulfillmentMode: "INSTANT",
  costMode: "NONE",
  reconRequired: false,
  reconActionPolicy: "REVIEW",
  adapterCode: "",
});
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageCategories({ page: page.value, pageSize });
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  records.value = parsed.data?.records ?? [];
  total.value = parsed.data?.total ?? 0;
}

function openCreate(): void {
  editing.value = null;
  form.code = "";
  form.name = "";
  form.rewardTarget = "PLATFORM";
  form.fulfillmentMode = "INSTANT";
  form.costMode = "NONE";
  form.reconRequired = false;
  form.reconActionPolicy = "REVIEW";
  form.adapterCode = "";
  formOpen.value = true;
}

function openEdit(row: PrizeCategoryResponse): void {
  editing.value = row;
  form.code = row.code ?? "";
  form.name = row.name ?? "";
  form.rewardTarget = row.rewardTarget ?? "PLATFORM";
  form.fulfillmentMode = row.fulfillmentMode ?? "INSTANT";
  form.costMode = row.costMode ?? "NONE";
  form.reconRequired = Boolean(row.reconRequired);
  form.reconActionPolicy = row.reconActionPolicy ?? "REVIEW";
  form.adapterCode = row.adapterCode ?? "";
  formOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const body = {
    code: form.code,
    name: form.name,
    rewardTarget: form.rewardTarget,
    fulfillmentMode: form.fulfillmentMode,
    costMode: form.costMode,
    reconRequired: form.reconRequired,
    reconActionPolicy: form.reconActionPolicy,
    adapterCode: form.adapterCode || undefined,
  };
  const result: Result = editing.value
    ? await updateCategory(form.code, body)
    : await createCategory(body);
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

function askDelete(row: PrizeCategoryResponse): void {
  if (!row.code || row.builtin) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deleteCategory(row.code as string);
      const parsed = writeOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

async function onDisable(row: PrizeCategoryResponse): Promise<void> {
  if (!row.code) {
    return;
  }
  const result = await disableCategory(row.code);
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

async function onEnable(row: PrizeCategoryResponse): Promise<void> {
  if (!row.code) {
    return;
  }
  const result = await enableCategory(row.code);
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
  <section class="admin-page" data-testid="category-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.category.title }}</h2>
      <a-button type="primary" v-auth="PERMS.REWARD_CAT_CREATE" data-testid="category-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-button type="primary" data-testid="category-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="category-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.REWARD_CAT_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.category.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.category.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.category.rewardTarget">
        <template #default="{ record: row }">{{ row.rewardTarget }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.category.fulfillmentMode">
        <template #default="{ record: row }">{{ row.fulfillmentMode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.category.reconActionPolicy">
        <template #default="{ record: row }">{{ row.reconActionPolicy }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.REWARD_CAT_UPDATE" data-testid="category-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" v-if="row.status === STATUS.ENABLED" v-auth="PERMS.REWARD_CAT_DISABLE" data-testid="category-disable" @click="onDisable(row)">
              {{ zhCN.common.disable }}
            </a-button>
            <a-button size="small" v-if="row.status === STATUS.DISABLED" v-auth="PERMS.REWARD_CAT_ENABLE" data-testid="category-enable" @click="onEnable(row)">
              {{ zhCN.common.enable }}
            </a-button>
            <a-button size="small" danger v-if="!row.builtin" v-auth="PERMS.REWARD_CAT_DELETE" data-testid="category-delete" @click="askDelete(row)">
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
      @submit="submit"
      @cancel="formOpen = false"
    >
      <a-form-item :label="zhCN.category.code">
        <a-input v-model:value="form.code" data-testid="category-code" :disabled="editing != null" required />
      </a-form-item>
      <a-form-item :label="zhCN.category.name">
        <a-input v-model:value="form.name" data-testid="category-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.category.rewardTarget">
        <a-select v-model:value="form.rewardTarget">
        <a-select-option v-for="item in REWARD_TARGETS" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.category.fulfillmentMode">
        <a-select v-model:value="form.fulfillmentMode">
        <a-select-option v-for="item in FULFILLMENT_MODES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.category.costMode">
        <a-select v-model:value="form.costMode">
        <a-select-option v-for="item in COST_MODES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.category.reconRequired">
        <a-checkbox v-model:checked="form.reconRequired" />
      </a-form-item>
      <a-form-item :label="zhCN.category.reconActionPolicy">
        <a-select v-model:value="form.reconActionPolicy">
        <a-select-option v-for="item in RECON_POLICIES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.category.adapterCode">
        <a-input v-model:value="form.adapterCode" />
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
