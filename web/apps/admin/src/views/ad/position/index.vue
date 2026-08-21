<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  bindPlacement,
  deletePosition,
  getPosition,
  pagePositions,
  savePosition,
  unbindPlacement,
  type AdPositionView,
} from "@/api/ad";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "AdPositionPage" });

const records = ref<AdPositionView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ code: "", form: "", status: "" });
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<AdPositionView | null>(null);
const form = reactive({
  code: "",
  name: "",
  form: "CAROUSEL",
  platforms: "WEB",
  status: "ENABLED",
  materialId: "",
  weight: "10",
  startTime: "",
  endTime: "",
});
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pagePositions({
    code: filters.code || undefined,
    form: filters.form || undefined,
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

function openCreate(): void {
  editing.value = null;
  form.code = "";
  form.name = "";
  form.form = "CAROUSEL";
  form.platforms = "WEB";
  form.status = "ENABLED";
  form.materialId = "";
  form.weight = "10";
  form.startTime = "";
  form.endTime = "";
  formOpen.value = true;
}

function openEdit(row: AdPositionView): void {
  editing.value = row;
  form.code = row.code;
  form.name = row.name;
  form.form = row.form;
  form.platforms = (row.platforms ?? []).join(",");
  form.status = row.status;
  form.materialId = "";
  form.weight = "10";
  form.startTime = "";
  form.endTime = "";
  formOpen.value = true;
}

function toInstant(value: string): string {
  if (!value) {
    return value;
  }
  return value.length === 16 ? `${value}:00Z` : value;
}

async function submit(): Promise<void> {
  saving.value = true;
  const result = await savePosition({
    id: editing.value?.id,
    code: form.code,
    name: form.name,
    form: form.form,
    platforms: form.platforms
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean),
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

async function onBind(): Promise<void> {
  if (!editing.value) {
    return;
  }
  const result = await bindPlacement(editing.value.id, {
    materialId: Number(form.materialId),
    weight: Number(form.weight),
    startTime: toInstant(form.startTime),
    endTime: toInstant(form.endTime),
    grayType: "NONE",
    status: "ENABLED",
  });
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  const detail = await getPosition(editing.value.id);
  const parsedDetail = okOrFeedback(detail);
  if (parsedDetail.ok && parsedDetail.data) {
    editing.value = parsedDetail.data;
  }
  await load();
}

function askDelete(row: AdPositionView): void {
  confirm.value = {
    message: zhCN.common.delete,
    run: async () => {
      const result = await deletePosition(row.id);
      const parsed = okOrFeedback(result);
      if (!parsed.ok) {
        feedback.value = parsed.feedback;
        return;
      }
      await load();
    },
  };
}

function askUnbind(row: AdPositionView, materialId: number): void {
  confirm.value = {
    message: zhCN.ad.unbind,
    run: async () => {
      const result = await unbindPlacement(row.id, materialId);
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
  <section class="admin-page" data-testid="ad-position-page">
    <h2>{{ zhCN.ad.positionTitle }}</h2>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.code" data-testid="filter-code" :placeholder="zhCN.ad.code" />
      <el-select v-model="filters.form" data-testid="filter-form">
        <el-option value="" :label="zhCN.ad.form" />
        <el-option value="CAROUSEL" label="CAROUSEL" />
        <el-option value="IMAGE" label="IMAGE" />
        <el-option value="SPLASH" label="SPLASH" />
        <el-option value="POPUP" label="POPUP" />
        <el-option value="FLOAT" label="FLOAT" />
      </el-select>
      <el-button data-testid="ad-position-query" @click="load">{{ zhCN.common.query }}</el-button>
      <el-button v-auth="PERMS.AD_POSITION_CREATE" data-testid="ad-position-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else :data="records" class="data-table" data-testid="ad-position-table" stripe>
      <el-table-column :label="zhCN.ad.code">
        <template #default="{ row }">{{ row.code }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.ad.name">
        <template #default="{ row }">{{ row.name }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.ad.form">
        <template #default="{ row }">{{ row.form }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.ad.overlap">
        <template #default="{ row }">{{ row.overlapCount }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-auth="PERMS.AD_POSITION_UPDATE" data-testid="ad-position-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
            <el-button v-auth="PERMS.AD_POSITION_DELETE" data-testid="ad-position-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </el-button>
            <el-button
              v-for="placement in row.placements"
              :key="placement.id"
              v-auth="PERMS.AD_POSITION_UPDATE"
              data-testid="ad-position-unbind"
              @click="askUnbind(row, placement.materialId)"
            >
              {{ zhCN.ad.unbind }} {{ placement.materialId }}
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
      <el-form-item :label="zhCN.ad.code">
        <el-input v-model="form.code" data-testid="ad-position-code" :disabled="editing != null" required />
      </el-form-item>
      <el-form-item :label="zhCN.ad.name">
        <el-input v-model="form.name" data-testid="ad-position-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.ad.form">
        <el-select v-model="form.form" data-testid="ad-position-form">
        <el-option value="CAROUSEL" label="CAROUSEL" />
        <el-option value="IMAGE" label="IMAGE" />
        <el-option value="SPLASH" label="SPLASH" />
        <el-option value="POPUP" label="POPUP" />
        <el-option value="FLOAT" label="FLOAT" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.ad.platforms">
        <el-input v-model="form.platforms" data-testid="ad-position-platforms" />
      </el-form-item>
      <template v-if="editing">
        <el-form-item :label="zhCN.ad.materialId">
        <el-input v-model="form.materialId" data-testid="ad-position-material-id" />
      </el-form-item>
        <el-form-item :label="zhCN.ad.weight">
        <el-input v-model="form.weight" data-testid="ad-position-weight" />
      </el-form-item>
        <el-form-item :label="zhCN.ad.startTime">
        <el-input v-model="form.startTime" type="datetime-local" data-testid="ad-position-start" />
      </el-form-item>
        <el-form-item :label="zhCN.ad.endTime">
        <el-input v-model="form.endTime" type="datetime-local" data-testid="ad-position-end" />
      </el-form-item>
        <el-button v-auth="PERMS.AD_POSITION_UPDATE" data-testid="ad-position-bind" @click="onBind">
          {{ zhCN.ad.bind }}
        </el-button>
      </template>
    </FormDialog>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>
