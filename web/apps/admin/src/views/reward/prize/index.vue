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
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { formatPrizeImpact, isPrizeImpactPreview } from "@/utils/prize-impact";

defineOptions({ name: "RewardPrizePage" });

const records = ref<PrizeResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
  const parsed = okOrFeedback(result);
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
  const parsed = okOrFeedback(result);
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
      const doneParsed = okOrFeedback(done);
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
      const doneParsed = okOrFeedback(done);
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
  <section class="admin-page" data-testid="prize-page">
    <h2>{{ zhCN.prize.title }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.code" data-testid="filter-code" :placeholder="zhCN.prize.code" />
      <input v-model="filters.name" data-testid="filter-name" :placeholder="zhCN.prize.name" />
      <input v-model="filters.categoryCode" data-testid="filter-category" :placeholder="zhCN.prize.category" />
      <select v-model="filters.status" data-testid="filter-status">
        <option value="">{{ zhCN.common.status }}</option>
        <option v-for="item in Object.values(PRIZE_STATUS)" :key="item" :value="item">{{ item }}</option>
      </select>
      <button type="button" data-testid="prize-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.REWARD_PRIZE_CREATE" type="button" data-testid="prize-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="prize-table">
      <thead>
        <tr>
          <th>{{ zhCN.prize.code }}</th>
          <th>{{ zhCN.prize.name }}</th>
          <th>{{ zhCN.prize.category }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.prize.stock }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.categoryCode }}</td>
          <td>{{ row.status }}</td>
          <td>{{ row.remainingStock }}/{{ row.totalStock }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.REWARD_PRIZE_UPDATE" type="button" data-testid="prize-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button
              v-if="row.status === PRIZE_STATUS.ENABLED"
              v-auth="PERMS.REWARD_PRIZE_DISABLE"
              type="button"
              data-testid="prize-disable"
              @click="askDisable(row)"
            >
              {{ zhCN.common.disable }}
            </button>
            <button
              v-if="row.status !== PRIZE_STATUS.ENABLED"
              v-auth="PERMS.REWARD_PRIZE_ENABLE"
              type="button"
              data-testid="prize-enable"
              @click="askEnable(row)"
            >
              {{ zhCN.common.enable }}
            </button>
            <button v-auth="PERMS.REWARD_PRIZE_STOCK" type="button" data-testid="prize-replenish" @click="openReplenish(row)">
              {{ zhCN.prize.replenish }}
            </button>
            <button
              v-if="row.status === PRIZE_STATUS.DRAFT"
              v-auth="PERMS.REWARD_PRIZE_DELETE"
              type="button"
              data-testid="prize-delete"
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
        <span>{{ zhCN.prize.code }}</span>
        <input v-model="form.code" data-testid="prize-code" :disabled="editing != null" required />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.name }}</span>
        <input v-model="form.name" data-testid="prize-name" required />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.category }}</span>
        <input v-model="form.categoryCode" data-testid="prize-category" required />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.totalStock }}</span>
        <input v-model.number="form.totalStock" type="number" required />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.dailyLimit }}</span>
        <input v-model.number="form.dailyClaimLimit" type="number" />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.totalLimit }}</span>
        <input v-model.number="form.totalClaimLimit" type="number" />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.claimMode }}</span>
        <select v-model="form.claimMode">
          <option v-for="item in CLAIM_MODES" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.category.reconActionPolicy }}</span>
        <select v-model="form.reconActionPolicy">
          <option value="">—</option>
          <option v-for="item in RECON_POLICIES" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.prize.unitCostFen }}</span>
        <input v-model="form.unitCostFen" />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.expireHours }}</span>
        <input v-model="form.expireHours" />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.typeParams }}</span>
        <textarea v-model="form.typeParams" rows="3" />
      </label>
    </FormDialog>
    <FormDialog
      :visible="replenishOpen"
      :title="zhCN.prize.replenish"
      :saving="saving"
      @submit="submitReplenish"
      @cancel="replenishOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.prize.amount }}</span>
        <input v-model.number="replenishForm.amount" data-testid="replenish-amount" type="number" min="1" required />
      </label>
      <label class="field">
        <span>{{ zhCN.prize.reason }}</span>
        <input v-model="replenishForm.reason" data-testid="replenish-reason" required />
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
