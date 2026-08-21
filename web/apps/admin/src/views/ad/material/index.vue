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
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.title" data-testid="filter-title" :placeholder="zhCN.ad.title" />
      <el-button data-testid="ad-material-query" @click="load">{{ zhCN.common.query }}</el-button>
      <el-button v-auth="PERMS.AD_MATERIAL_CREATE" data-testid="ad-material-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="records" class="data-table" data-testid="ad-material-table" stripe>
      <el-table-column :label="zhCN.ad.title">
        <template #default="{ row }">{{ row.title }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.ad.weight">
        <template #default="{ row }">{{ row.weight }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-auth="PERMS.AD_MATERIAL_UPDATE" data-testid="ad-material-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button v-auth="PERMS.AD_MATERIAL_DELETE" data-testid="ad-material-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <el-form-item :label="zhCN.ad.title">
        <el-input v-model="form.title" data-testid="ad-material-title" required />
      </el-form-item>
      <el-form-item :label="zhCN.ad.imageUrl">
        <el-input v-model="form.imageUrl" data-testid="ad-material-image" required />
      </el-form-item>
      <el-form-item :label="zhCN.ad.weight">
        <el-input v-model="form.weight" data-testid="ad-material-weight" required />
      </el-form-item>
      <el-form-item :label="zhCN.ad.startTime">
        <el-input v-model="form.startTime" type="datetime-local" data-testid="ad-material-start" required />
      </el-form-item>
      <el-form-item :label="zhCN.ad.endTime">
        <el-input v-model="form.endTime" type="datetime-local" data-testid="ad-material-end" required />
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
