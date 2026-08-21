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
    <h2>{{ zhCN.category.title }}</h2>
    <div class="admin-toolbar">
      <button type="button" data-testid="category-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.REWARD_CAT_CREATE" type="button" data-testid="category-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="category-table">
      <thead>
        <tr>
          <th>{{ zhCN.category.code }}</th>
          <th>{{ zhCN.category.name }}</th>
          <th>{{ zhCN.category.rewardTarget }}</th>
          <th>{{ zhCN.category.fulfillmentMode }}</th>
          <th>{{ zhCN.category.reconActionPolicy }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.code">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.rewardTarget }}</td>
          <td>{{ row.fulfillmentMode }}</td>
          <td>{{ row.reconActionPolicy }}</td>
          <td>{{ row.status }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.REWARD_CAT_UPDATE" type="button" data-testid="category-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button
              v-if="row.status === STATUS.ENABLED"
              v-auth="PERMS.REWARD_CAT_DISABLE"
              type="button"
              data-testid="category-disable"
              @click="onDisable(row)"
            >
              {{ zhCN.common.disable }}
            </button>
            <button
              v-if="row.status === STATUS.DISABLED"
              v-auth="PERMS.REWARD_CAT_ENABLE"
              type="button"
              data-testid="category-enable"
              @click="onEnable(row)"
            >
              {{ zhCN.common.enable }}
            </button>
            <button
              v-if="!row.builtin"
              v-auth="PERMS.REWARD_CAT_DELETE"
              type="button"
              data-testid="category-delete"
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
      @submit="submit"
      @cancel="formOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.category.code }}</span>
        <input v-model="form.code" data-testid="category-code" :disabled="editing != null" required />
      </label>
      <label class="field">
        <span>{{ zhCN.category.name }}</span>
        <input v-model="form.name" data-testid="category-name" required />
      </label>
      <label class="field">
        <span>{{ zhCN.category.rewardTarget }}</span>
        <select v-model="form.rewardTarget">
          <option v-for="item in REWARD_TARGETS" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.category.fulfillmentMode }}</span>
        <select v-model="form.fulfillmentMode">
          <option v-for="item in FULFILLMENT_MODES" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.category.costMode }}</span>
        <select v-model="form.costMode">
          <option v-for="item in COST_MODES" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.category.reconRequired }}</span>
        <input v-model="form.reconRequired" type="checkbox" />
      </label>
      <label class="field">
        <span>{{ zhCN.category.reconActionPolicy }}</span>
        <select v-model="form.reconActionPolicy">
          <option v-for="item in RECON_POLICIES" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.category.adapterCode }}</span>
        <input v-model="form.adapterCode" />
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
