<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import type { Result } from "@mkt/shared";
import {
  createPrize,
  deletePrize,
  disablePrize,
  enablePrize,
  pagePrizes,
  replenishStock,
  updatePrize,
  type PrizeImpactResponse,
  type PrizeResponse,
} from "@/api/reward";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { CLAIM_MODES, PRIZE_STATUS, RECON_POLICIES } from "@/constants/reward";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { formatPrizeImpact, isPrizeImpactPreview } from "@/utils/prize-impact";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "RewardPrizePage" });

const records = ref<PrizeResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ code: "", name: "", categoryCode: "", status: "" });
const formOpen = ref(false);
const replenishOpen = ref(false);
const saving = ref(false);
const editing = ref<PrizeResponse | null>(null);
const form = reactive({
  code: "",
  name: "",
  categoryCode: "",
  totalStock: 0,
  dailyClaimLimit: 0,
  totalClaimLimit: 0,
  claimMode: "AUTO",
  reconActionPolicy: "",
  unitCostFen: "",
  expireHours: "",
  typeParams: "{}",
});
const replenishForm = reactive({ amount: 1, reason: "" });
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pagePrizes({
    code: filters.code,
    name: filters.name,
    categoryCode: filters.categoryCode,
    status: filters.status,
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

function parseTypeParams(): Record<string, unknown> | undefined {
  if (!form.typeParams.trim()) {
    return undefined;
  }
  try {
    return JSON.parse(form.typeParams) as Record<string, unknown>;
  } catch {
    return undefined;
  }
}

function buildBody() {
  return {
    code: form.code,
    name: form.name,
    categoryCode: form.categoryCode,
    totalStock: Number(form.totalStock),
    dailyClaimLimit: Number(form.dailyClaimLimit),
    totalClaimLimit: Number(form.totalClaimLimit),
    claimMode: form.claimMode,
    reconActionPolicy: form.reconActionPolicy || undefined,
    unitCostFen: form.unitCostFen ? Number(form.unitCostFen) : undefined,
    expireHours: form.expireHours ? Number(form.expireHours) : undefined,
    typeParams: parseTypeParams(),
  };
}

function openCreate(): void {
  editing.value = null;
  form.code = "";
  form.name = "";
  form.categoryCode = "";
  form.totalStock = 0;
  form.dailyClaimLimit = 0;
  form.totalClaimLimit = 0;
  form.claimMode = "AUTO";
  form.reconActionPolicy = "";
  form.unitCostFen = "";
  form.expireHours = "";
  form.typeParams = "{}";
  formOpen.value = true;
}

function openEdit(row: PrizeResponse): void {
  editing.value = row;
  form.code = row.code ?? "";
  form.name = row.name ?? "";
  form.categoryCode = row.categoryCode ?? "";
  form.totalStock = row.totalStock ?? 0;
  form.dailyClaimLimit = row.dailyClaimLimit ?? 0;
  form.totalClaimLimit = row.totalClaimLimit ?? 0;
  form.claimMode = row.claimMode ?? "AUTO";
  form.reconActionPolicy = row.reconActionPolicy ?? "";
  form.unitCostFen = row.unitCostFen != null ? String(row.unitCostFen) : "";
  form.expireHours = row.expireHours != null ? String(row.expireHours) : "";
  form.typeParams = JSON.stringify(row.typeParams ?? {});
  formOpen.value = true;
}

function openReplenish(row: PrizeResponse): void {
  editing.value = row;
  replenishForm.amount = 1;
  replenishForm.reason = "";
  replenishOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result: Result = editing.value?.id != null ? await updatePrize(editing.value.id, buildBody()) : await createPrize(buildBody());
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function submitReplenish(): Promise<void> {
  if (editing.value?.id == null) {
    return;
  }
  saving.value = true;
  const result = await replenishStock(editing.value.id, {
    amount: Number(replenishForm.amount),
    reason: replenishForm.reason,
  });
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  replenishOpen.value = false;
  await load();
}

async function askDisable(row: PrizeResponse): Promise<void> {
  if (row.id == null) {
    return;
  }
  feedback.value = null;
  const preview = await disablePrize(row.id, { confirm: false });
  const parsed = okOrFeedback(preview);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  if (!isPrizeImpactPreview(parsed.data)) {
    await load();
    return;
  }
  confirm.value = {
    message: formatPrizeImpact(parsed.data as PrizeImpactResponse, "disable"),
    run: async () => {
      const done = await disablePrize(row.id as number, { confirm: true });
      const doneParsed = writeOrFeedback(done);
      if (!doneParsed.ok) {
        feedback.value = doneParsed.feedback;
        return;
      }
      await load();
    },
  };
}

async function askEnable(row: PrizeResponse): Promise<void> {
  if (row.id == null) {
    return;
  }
  feedback.value = null;
  const preview = await enablePrize(row.id, { confirm: false });
  const parsed = okOrFeedback(preview);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  if (!isPrizeImpactPreview(parsed.data)) {
    await load();
    return;
  }
  confirm.value = {
    message: formatPrizeImpact(parsed.data as PrizeImpactResponse, "enable"),
    run: async () => {
      const done = await enablePrize(row.id as number, { confirm: true });
      const doneParsed = writeOrFeedback(done);
      if (!doneParsed.ok) {
        feedback.value = doneParsed.feedback;
        return;
      }
      await load();
    },
  };
}

function askDelete(row: PrizeResponse): void {
  if (row.id == null) {
    return;
  }
  confirm.value = {
    message: zhCN.confirm.delete,
    run: async () => {
      const result = await deletePrize(row.id as number);
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
  <section class="admin-page" data-testid="prize-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.prize.title }}</h2>
      <a-button type="primary" v-auth="PERMS.REWARD_PRIZE_CREATE" data-testid="prize-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.code" data-testid="filter-code" :placeholder="zhCN.prize.code" />
      <a-input v-model:value="filters.name" data-testid="filter-name" :placeholder="zhCN.prize.name" />
      <a-input v-model:value="filters.categoryCode" data-testid="filter-category" :placeholder="zhCN.prize.category" />
      <a-select v-model:value="filters.status" data-testid="filter-status">
        <a-select-option value="">{{ zhCN.common.status }}</a-select-option>
        <a-select-option v-for="item in Object.values(PRIZE_STATUS)" :key="item" :value="item">{{ adminStatusLabel(item) }}</a-select-option>
      </a-select>
      <a-button type="primary" data-testid="prize-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="prize-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.REWARD_PRIZE_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.prize.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.prize.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.prize.category">
        <template #default="{ record: row }">{{ row.categoryCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">
          <a-tag :color="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'default'" :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'">
            {{ adminStatusLabel(row.status) }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.prize.stock">
        <template #default="{ record: row }">{{ row.remainingStock }}/{{ row.totalStock }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-auth="PERMS.REWARD_PRIZE_UPDATE" data-testid="prize-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" v-if="row.status === PRIZE_STATUS.ENABLED" v-auth="PERMS.REWARD_PRIZE_DISABLE" data-testid="prize-disable" @click="askDisable(row)">
              {{ zhCN.common.disable }}
            </a-button>
            <a-button size="small" v-if="row.status !== PRIZE_STATUS.ENABLED" v-auth="PERMS.REWARD_PRIZE_ENABLE" data-testid="prize-enable" @click="askEnable(row)">
              {{ zhCN.common.enable }}
            </a-button>
            <a-button size="small" v-auth="PERMS.REWARD_PRIZE_STOCK" data-testid="prize-replenish" @click="openReplenish(row)">
              {{ zhCN.prize.replenish }}
            </a-button>
            <a-button size="small" danger v-if="row.status === PRIZE_STATUS.DRAFT" v-auth="PERMS.REWARD_PRIZE_DELETE" data-testid="prize-delete" @click="askDelete(row)">
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
      <a-form-item :label="zhCN.prize.code">
        <a-input v-model:value="form.code" data-testid="prize-code" :disabled="editing != null" required />
      </a-form-item>
      <a-form-item :label="zhCN.prize.name">
        <a-input v-model:value="form.name" data-testid="prize-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.prize.category">
        <a-input v-model:value="form.categoryCode" data-testid="prize-category" required />
      </a-form-item>
      <a-form-item :label="zhCN.prize.totalStock">
        <a-input v-model:value.number="form.totalStock" required type="number" />
      </a-form-item>
      <a-form-item :label="zhCN.prize.dailyLimit">
        <a-input v-model:value.number="form.dailyClaimLimit" type="number" />
      </a-form-item>
      <a-form-item :label="zhCN.prize.totalLimit">
        <a-input v-model:value.number="form.totalClaimLimit" type="number" />
      </a-form-item>
      <a-form-item :label="zhCN.prize.claimMode">
        <a-select v-model:value="form.claimMode">
        <a-select-option v-for="item in CLAIM_MODES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.category.reconActionPolicy">
        <a-select v-model:value="form.reconActionPolicy">
        <a-select-option value="">—</a-select-option>
        <a-select-option v-for="item in RECON_POLICIES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.prize.unitCostFen">
        <a-input v-model:value="form.unitCostFen" />
      </a-form-item>
      <a-form-item :label="zhCN.prize.expireHours">
        <a-input v-model:value="form.expireHours" />
      </a-form-item>
      <a-form-item :label="zhCN.prize.typeParams">
        <a-textarea v-model:value="form.typeParams" :rows="3" />
      </a-form-item>
    </FormDialog>
    <FormDialog
      :visible="replenishOpen"
      :title="zhCN.prize.replenish"
      :saving="saving"
      @submit="submitReplenish"
      @cancel="replenishOpen = false"
    >
      <a-form-item :label="zhCN.prize.amount">
        <a-input v-model:value.number="replenishForm.amount" data-testid="replenish-amount" min="1" required type="number" />
      </a-form-item>
      <a-form-item :label="zhCN.prize.reason">
        <a-input v-model:value="replenishForm.reason" data-testid="replenish-reason" required />
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
