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
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "RewardCategoryPage" });

const records = ref<PrizeCategoryResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
  const parsed = okOrFeedback(result);
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
      const parsed = okOrFeedback(result);
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
  const parsed = okOrFeedback(result);
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
  <section class="admin-page" data-testid="category-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.category.title }}</h2>
      <el-button type="primary" v-auth="PERMS.REWARD_CAT_CREATE" data-testid="category-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-button data-testid="category-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.REWARD_CAT_CREATE" text type="primary" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="category-table" size="small" stripe>
      <el-table-column :label="zhCN.category.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.category.name">
        <template #default="{ row }">{{ row.name }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.category.rewardTarget">
        <template #default="{ row }">{{ row.rewardTarget }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.category.fulfillmentMode">
        <template #default="{ row }">{{ row.fulfillmentMode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.category.reconActionPolicy">
        <template #default="{ row }">{{ row.reconActionPolicy }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'info'"
            :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'"
          >
            {{ adminStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.REWARD_CAT_UPDATE" data-testid="category-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button text
              v-if="row.status === STATUS.ENABLED"
              v-auth="PERMS.REWARD_CAT_DISABLE"
              data-testid="category-disable"
              @click="onDisable(row)"
            >
              {{ zhCN.common.disable }}
            </el-button>
            <el-button text
              v-if="row.status === STATUS.DISABLED"
              v-auth="PERMS.REWARD_CAT_ENABLE"
              data-testid="category-enable"
              @click="onEnable(row)"
            >
              {{ zhCN.common.enable }}
            </el-button>
            <el-button text
              v-if="!row.builtin"
              v-auth="PERMS.REWARD_CAT_DELETE"
              data-testid="category-delete"
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
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.prevPage }}</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.nextPage }}</el-button>
    </div>
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <el-form-item :label="zhCN.category.code">
        <el-input v-model="form.code" data-testid="category-code" :disabled="editing != null" required />
      </el-form-item>
      <el-form-item :label="zhCN.category.name">
        <el-input v-model="form.name" data-testid="category-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.category.rewardTarget">
        <el-select v-model="form.rewardTarget">
        <el-option v-for="item in REWARD_TARGETS" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.category.fulfillmentMode">
        <el-select v-model="form.fulfillmentMode">
        <el-option v-for="item in FULFILLMENT_MODES" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.category.costMode">
        <el-select v-model="form.costMode">
        <el-option v-for="item in COST_MODES" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.category.reconRequired">
        <el-checkbox v-model="form.reconRequired" />
      </el-form-item>
      <el-form-item :label="zhCN.category.reconActionPolicy">
        <el-select v-model="form.reconActionPolicy">
        <el-option v-for="item in RECON_POLICIES" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.category.adapterCode">
        <el-input v-model="form.adapterCode" />
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
