<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  addListItem,
  importListItems,
  pageListItems,
  removeListItem,
  type RiskListItemResponse,
} from "@/api/risk";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { RISK_DIMENSIONS, RISK_LIST_TYPES } from "@/constants/risk";
import { hasAuth } from "@/directives/auth";
import { zhCN } from "@/locales/zh-CN";
import { formatDateTime, toIsoInstant } from "@/utils/datetime";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "RiskListItemPage" });

const records = ref<RiskListItemResponse[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ dimension: "", listType: "", value: "", from: "", to: "" });
const formOpen = ref(false);
const importOpen = ref(false);
const removeOpen = ref(false);
const saving = ref(false);
const importHint = ref("");
const removing = ref<RiskListItemResponse | null>(null);
const removeReason = ref("");
const form = reactive({
  dimension: "USER" as (typeof RISK_DIMENSIONS)[number],
  listType: "BLACK" as (typeof RISK_LIST_TYPES)[number],
  listValue: "",
  reason: "",
  expireAt: "",
  denyLogin: false,
  remark: "",
});

const canAdd = computed(() => hasAuth(PERMS.RISK_BLACK_ADD) || hasAuth(PERMS.RISK_WHITE_ADD));
const showDenyLogin = computed(() => form.dimension === "USER" && form.listType === "BLACK");

function removePerm(row: RiskListItemResponse): string {
  return row.listType === "WHITE" ? PERMS.RISK_WHITE_REMOVE : PERMS.RISK_BLACK_REMOVE;
}

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageListItems({
    dimension: (filters.dimension || undefined) as "USER" | "IP" | "DEVICE" | undefined,
    listType: (filters.listType || undefined) as "BLACK" | "WHITE" | undefined,
    value: filters.value,
    from: toIsoInstant(filters.from),
    to: toIsoInstant(filters.to),
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

function openCreate(): void {
  form.dimension = "USER";
  form.listType = hasAuth(PERMS.RISK_BLACK_ADD) ? "BLACK" : "WHITE";
  form.listValue = "";
  form.reason = "";
  form.expireAt = "";
  form.denyLogin = false;
  form.remark = "";
  formOpen.value = true;
}

function openImport(): void {
  form.dimension = "USER";
  form.listValue = "";
  form.reason = "";
  importHint.value = "";
  importOpen.value = true;
}

function openRemove(row: RiskListItemResponse): void {
  removing.value = row;
  removeReason.value = "";
  removeOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result = await addListItem({
    dimension: form.dimension,
    listType: form.listType,
    listValue: form.listValue,
    reason: form.reason,
    expireAt: toIsoInstant(form.expireAt),
    denyLogin: showDenyLogin.value ? form.denyLogin : undefined,
    remark: form.remark || undefined,
  });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

async function submitImport(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result = await importListItems({
    dimension: form.dimension,
    listType: "BLACK",
    content: form.listValue,
    reason: form.reason,
  });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  importHint.value = zhCN.list.importResult
    .replace("{imported}", String(parsed.data?.imported ?? 0))
    .replace("{invalid}", String(parsed.data?.invalid ?? 0));
  await load();
}

async function submitRemove(): Promise<void> {
  if (removing.value?.id == null) {
    return;
  }
  saving.value = true;
  feedback.value = null;
  const result = await removeListItem(removing.value.id, { reason: removeReason.value });
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  removeOpen.value = false;
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="list-item-page">
    <h2>{{ zhCN.list.title }}</h2>
    <div class="admin-toolbar">
      <select v-model="filters.dimension" data-testid="filter-dimension">
        <option value="">{{ zhCN.list.dimension }}</option>
        <option v-for="item in RISK_DIMENSIONS" :key="item" :value="item">{{ item }}</option>
      </select>
      <select v-model="filters.listType" data-testid="filter-list-type">
        <option value="">{{ zhCN.list.listType }}</option>
        <option v-for="item in RISK_LIST_TYPES" :key="item" :value="item">{{ item }}</option>
      </select>
      <input v-model="filters.value" data-testid="filter-value" :placeholder="zhCN.list.listValue" />
      <input v-model="filters.from" data-testid="filter-from" type="datetime-local" />
      <input v-model="filters.to" data-testid="filter-to" type="datetime-local" />
      <button type="button" data-testid="list-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-if="canAdd" type="button" data-testid="list-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
      <button v-auth="PERMS.RISK_BLACK_IMPORT" type="button" data-testid="list-import" @click="openImport">
        {{ zhCN.list.import }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="list-table">
      <thead>
        <tr>
          <th>{{ zhCN.list.dimension }}</th>
          <th>{{ zhCN.list.listType }}</th>
          <th>{{ zhCN.list.listValue }}</th>
          <th>{{ zhCN.list.reason }}</th>
          <th>{{ zhCN.list.denyLogin }}</th>
          <th>{{ zhCN.list.effectiveAt }}</th>
          <th>{{ zhCN.list.expireAt }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.dimension }}</td>
          <td>{{ row.listType }}</td>
          <td>{{ row.listValue }}</td>
          <td>{{ row.reason }}</td>
          <td>{{ row.denyLogin ? zhCN.common.enabled : zhCN.common.disabled }}</td>
          <td>{{ formatDateTime(row.effectiveAt) }}</td>
          <td>{{ formatDateTime(row.expireAt) }}</td>
          <td class="row-actions">
            <button
              v-auth="removePerm(row)"
              type="button"
              data-testid="list-remove"
              @click="openRemove(row)"
            >
              {{ zhCN.list.remove }}
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
      :title="zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.list.dimension }}</span>
        <select v-model="form.dimension" data-testid="list-dimension">
          <option v-for="item in RISK_DIMENSIONS" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.list.listType }}</span>
        <select v-model="form.listType" data-testid="list-type">
          <option v-if="hasAuth(PERMS.RISK_BLACK_ADD)" value="BLACK">{{ zhCN.list.black }}</option>
          <option v-if="hasAuth(PERMS.RISK_WHITE_ADD)" value="WHITE">{{ zhCN.list.white }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.list.listValue }}</span>
        <input v-model="form.listValue" data-testid="list-value" required />
      </label>
      <label class="field">
        <span>{{ zhCN.list.reason }}</span>
        <input v-model="form.reason" data-testid="list-reason" required />
      </label>
      <label class="field">
        <span>{{ zhCN.list.expireAt }}</span>
        <input v-model="form.expireAt" data-testid="list-expire" type="datetime-local" />
      </label>
      <label v-if="showDenyLogin" class="field">
        <span>{{ zhCN.list.denyLogin }}</span>
        <input v-model="form.denyLogin" data-testid="list-deny-login" type="checkbox" />
      </label>
      <label class="field">
        <span>{{ zhCN.common.remark }}</span>
        <input v-model="form.remark" data-testid="list-remark" />
      </label>
    </FormDialog>
    <FormDialog
      :visible="importOpen"
      :title="zhCN.list.import"
      :saving="saving"
      @submit="submitImport"
      @cancel="importOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.list.dimension }}</span>
        <select v-model="form.dimension" data-testid="import-dimension">
          <option v-for="item in RISK_DIMENSIONS" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.list.content }}</span>
        <textarea v-model="form.listValue" data-testid="import-content" rows="8" required />
      </label>
      <label class="field">
        <span>{{ zhCN.list.reason }}</span>
        <input v-model="form.reason" data-testid="import-reason" required />
      </label>
      <p v-if="importHint" data-testid="import-result">{{ importHint }}</p>
    </FormDialog>
    <FormDialog
      :visible="removeOpen"
      :title="zhCN.list.remove"
      :saving="saving"
      @submit="submitRemove"
      @cancel="removeOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.list.removeReason }}</span>
        <input v-model="removeReason" data-testid="remove-reason" required />
      </label>
    </FormDialog>
  </section>
</template>
