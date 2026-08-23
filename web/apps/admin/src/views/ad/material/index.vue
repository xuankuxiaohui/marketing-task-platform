<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { deleteMaterial, pageMaterials, saveMaterial, type AdMaterialView } from "@/api/ad";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "AdMaterialPage" });

const records = ref<AdMaterialView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="ad-material-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.ad.materialTitle }}</h2>
      <a-button type="primary" v-auth="PERMS.AD_MATERIAL_CREATE" data-testid="ad-material-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.title" data-testid="filter-title" :placeholder="zhCN.ad.title" />
      <a-button type="primary" data-testid="ad-material-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="ad-material-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.AD_MATERIAL_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.ad.title">
        <template #default="{ record: row }">{{ row.title }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.ad.weight">
        <template #default="{ record: row }">{{ row.weight }}</template>
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
            <a-button size="small" v-auth="PERMS.AD_MATERIAL_UPDATE" data-testid="ad-material-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" danger v-auth="PERMS.AD_MATERIAL_DELETE" data-testid="ad-material-delete" @click="askDelete(row)">
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
      <a-form-item :label="zhCN.ad.title">
        <a-input v-model:value="form.title" data-testid="ad-material-title" required />
      </a-form-item>
      <a-form-item :label="zhCN.ad.imageUrl">
        <a-input v-model:value="form.imageUrl" data-testid="ad-material-image" required />
      </a-form-item>
      <a-form-item :label="zhCN.ad.weight">
        <a-input v-model:value="form.weight" data-testid="ad-material-weight" required />
      </a-form-item>
      <a-form-item :label="zhCN.ad.startTime">
        <a-date-picker v-model:value="form.startTime" data-testid="ad-material-start" required show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
      <a-form-item :label="zhCN.ad.endTime">
        <a-date-picker v-model:value="form.endTime" data-testid="ad-material-end" required show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
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
