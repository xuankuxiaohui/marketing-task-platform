<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { deleteMaterial, pageMaterials, saveMaterial, type AdMaterialView } from "@/api/ad";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "AdMaterialPage" });

const records = ref<AdMaterialView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ title: "", status: "" });
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<AdMaterialView | null>(null);
const form = reactive({
  title: "",
  subtitle: "",
  imageUrl: "",
  jumpType: "NONE",
  jumpParams: "",
  weight: "10",
  startTime: "",
  endTime: "",
  status: "ENABLED",
});
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageMaterials({
    title: filters.title || undefined,
    status: filters.status || undefined,
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

function toInstant(value: string): string {
  if (!value) {
    return value;
  }
  return value.length === 16 ? `${value}:00Z` : value;
}

function openCreate(): void {
  editing.value = null;
  form.title = "";
  form.subtitle = "";
  form.imageUrl = "";
  form.jumpType = "NONE";
  form.jumpParams = "";
  form.weight = "10";
  form.startTime = "";
  form.endTime = "";
  form.status = "ENABLED";
  formOpen.value = true;
}

function openEdit(row: AdMaterialView): void {
  editing.value = row;
  form.title = row.title;
  form.subtitle = row.subtitle ?? "";
  form.imageUrl = row.imageUrl;
  form.jumpType = row.jumpType;
  form.jumpParams = row.jumpParams ? JSON.stringify(row.jumpParams) : "";
  form.weight = String(row.weight);
  form.startTime = row.startTime ? row.startTime.slice(0, 16) : "";
  form.endTime = row.endTime ? row.endTime.slice(0, 16) : "";
  form.status = row.status;
  formOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  let params: Record<string, unknown> | undefined;
  if (form.jumpParams.trim()) {
    params = JSON.parse(form.jumpParams) as Record<string, unknown>;
  }
  const result = await saveMaterial({
    id: editing.value?.id,
    title: form.title,
    subtitle: form.subtitle || undefined,
    imageUrl: form.imageUrl,
    jumpType: form.jumpType,
    jumpParams: params,
    weight: Number(form.weight),
    startTime: toInstant(form.startTime),
    endTime: toInstant(form.endTime),
    status: form.status,
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

function askDelete(row: AdMaterialView): void {
  confirm.value = {
    message: zhCN.common.delete,
    run: async () => {
      const result = await deleteMaterial(row.id);
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
  <section class="admin-page" data-testid="ad-material-page">
    <h2>{{ zhCN.ad.materialTitle }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.title" data-testid="filter-title" :placeholder="zhCN.ad.title" />
      <button type="button" data-testid="ad-material-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.AD_MATERIAL_CREATE" type="button" data-testid="ad-material-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="ad-material-table">
      <thead>
        <tr>
          <th>{{ zhCN.ad.title }}</th>
          <th>{{ zhCN.ad.weight }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.id">
          <td>{{ row.title }}</td>
          <td>{{ row.weight }}</td>
          <td>{{ row.status }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.AD_MATERIAL_UPDATE" type="button" data-testid="ad-material-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </button>
            <button v-auth="PERMS.AD_MATERIAL_DELETE" type="button" data-testid="ad-material-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <label class="field">
        <span>{{ zhCN.ad.title }}</span>
        <input v-model="form.title" data-testid="ad-material-title" required />
      </label>
      <label class="field">
        <span>{{ zhCN.ad.imageUrl }}</span>
        <input v-model="form.imageUrl" data-testid="ad-material-image" required />
      </label>
      <label class="field">
        <span>{{ zhCN.ad.weight }}</span>
        <input v-model="form.weight" data-testid="ad-material-weight" required />
      </label>
      <label class="field">
        <span>{{ zhCN.ad.startTime }}</span>
        <input v-model="form.startTime" type="datetime-local" data-testid="ad-material-start" required />
      </label>
      <label class="field">
        <span>{{ zhCN.ad.endTime }}</span>
        <input v-model="form.endTime" type="datetime-local" data-testid="ad-material-end" required />
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
