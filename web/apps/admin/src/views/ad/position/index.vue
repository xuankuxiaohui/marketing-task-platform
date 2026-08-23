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
import { adminStatusLabel } from "@/utils/status-label";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "AdPositionPage" });

const records = ref<AdPositionView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
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


function onTableChange(pag: { current?: number }): void {
  page.value = pag.current ?? 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="ad-position-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.ad.positionTitle }}</h2>
      <a-button type="primary" v-auth="PERMS.AD_POSITION_CREATE" data-testid="ad-position-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.code" data-testid="filter-code" :placeholder="zhCN.ad.code" />
      <a-select v-model:value="filters.form" data-testid="filter-form">
        <a-select-option value="">{{ zhCN.ad.form }}</a-select-option>
        <a-select-option value="CAROUSEL">CAROUSEL</a-select-option>
        <a-select-option value="IMAGE">IMAGE</a-select-option>
        <a-select-option value="SPLASH">SPLASH</a-select-option>
        <a-select-option value="POPUP">POPUP</a-select-option>
        <a-select-option value="FLOAT">FLOAT</a-select-option>
      </a-select>
      <a-button type="primary" data-testid="ad-position-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="ad-position-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.AD_POSITION_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.ad.code">
        <template #default="{ record: row }">{{ row.code }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.ad.name">
        <template #default="{ record: row }">{{ row.name }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.ad.form">
        <template #default="{ record: row }">{{ row.form }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.ad.overlap">
        <template #default="{ record: row }">{{ row.overlapCount }}</template>
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
            <a-button size="small" v-auth="PERMS.AD_POSITION_UPDATE" data-testid="ad-position-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
            <a-button size="small" danger v-auth="PERMS.AD_POSITION_DELETE" data-testid="ad-position-delete" @click="askDelete(row)">
              {{ zhCN.common.delete }}
            </a-button>
            <a-button size="small" v-for="placement in row.placements" :key="placement.id" v-auth="PERMS.AD_POSITION_UPDATE" data-testid="ad-position-unbind" @click="askUnbind(row, placement.materialId)">
              {{ zhCN.ad.unbind }} {{ placement.materialId }}
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
      <a-form-item :label="zhCN.ad.code">
        <a-input v-model:value="form.code" data-testid="ad-position-code" :disabled="editing != null" required />
      </a-form-item>
      <a-form-item :label="zhCN.ad.name">
        <a-input v-model:value="form.name" data-testid="ad-position-name" required />
      </a-form-item>
      <a-form-item :label="zhCN.ad.form">
        <a-select v-model:value="form.form" data-testid="ad-position-form">
        <a-select-option value="CAROUSEL">CAROUSEL</a-select-option>
        <a-select-option value="IMAGE">IMAGE</a-select-option>
        <a-select-option value="SPLASH">SPLASH</a-select-option>
        <a-select-option value="POPUP">POPUP</a-select-option>
        <a-select-option value="FLOAT">FLOAT</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.ad.platforms">
        <a-input v-model:value="form.platforms" data-testid="ad-position-platforms" />
      </a-form-item>
      <template v-if="editing">
        <a-form-item :label="zhCN.ad.materialId">
        <a-input v-model:value="form.materialId" data-testid="ad-position-material-id" />
      </a-form-item>
        <a-form-item :label="zhCN.ad.weight">
        <a-input v-model:value="form.weight" data-testid="ad-position-weight" />
      </a-form-item>
        <a-form-item :label="zhCN.ad.startTime">
        <a-date-picker v-model:value="form.startTime" data-testid="ad-position-start" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
        <a-form-item :label="zhCN.ad.endTime">
        <a-date-picker v-model:value="form.endTime" data-testid="ad-position-end" show-time value-format="YYYY-MM-DDTHH:mm" format="YYYY-MM-DD HH:mm" />
      </a-form-item>
        <a-button v-auth="PERMS.AD_POSITION_UPDATE" data-testid="ad-position-bind" @click="onBind">
          {{ zhCN.ad.bind }}
        </a-button>
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
