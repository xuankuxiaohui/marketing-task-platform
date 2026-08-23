<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { createConfig, pageConfigs, updateConfig, type ConfigView } from "@/api/system";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { CONFIG_VALUE_TYPES, PERMS, STATUS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { buildConfigUpdateBody, CONFIG_MASK_DISPLAY } from "@/utils/config-update";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminPagination, adminRowKey } from "@/utils/table";

defineOptions({ name: "ConfigManagePage" });

const records = ref<ConfigView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ADMIN_PAGE_SIZE;
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const filters = reactive({ configGroup: "", key: "" });
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<ConfigView | null>(null);
const form = reactive({
  configKey: "",
  configGroup: "",
  configValue: "",
  valueType: "STRING",
  masked: false,
  remark: "",
  status: STATUS.ENABLED as string,
});

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await pageConfigs({
    configGroup: filters.configGroup,
    key: filters.key,
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
  form.configKey = "";
  form.configGroup = "";
  form.configValue = "";
  form.valueType = "STRING";
  form.masked = false;
  form.remark = "";
  form.status = STATUS.ENABLED;
  formOpen.value = true;
}

function openEdit(row: ConfigView): void {
  editing.value = row;
  form.configKey = row.configKey ?? "";
  form.configGroup = row.configGroup ?? "";
  form.configValue = row.masked ? CONFIG_MASK_DISPLAY : (row.configValue ?? "");
  form.valueType = row.valueType ?? "STRING";
  form.masked = Boolean(row.masked);
  form.remark = row.remark ?? "";
  form.status = row.status ?? STATUS.ENABLED;
  formOpen.value = true;
}

async function submit(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  if (!editing.value) {
    const result = await createConfig({
      configKey: form.configKey,
      configGroup: form.configGroup,
      configValue: form.configValue,
      valueType: form.valueType,
      masked: form.masked,
      remark: form.remark || undefined,
    });
    saving.value = false;
    const parsed = writeOrFeedback(result);
    if (!parsed.ok) {
      feedback.value = parsed.feedback;
      return;
    }
    formOpen.value = false;
    await load();
    return;
  }
  const body = buildConfigUpdateBody(
    { configValue: editing.value.configValue, masked: editing.value.masked },
    {
      configGroup: form.configGroup,
      status: form.status,
      valueType: form.valueType,
      masked: form.masked,
      remark: form.remark,
      value: form.configValue,
    },
  );
  const result = await updateConfig(editing.value.configKey ?? form.configKey, body);
  saving.value = false;
  const parsed = writeOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
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
  <section class="admin-page" data-testid="config-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.config.title }}</h2>
      <a-button type="primary" v-auth="PERMS.CONFIG_CREATE" data-testid="config-create" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="filters.configGroup" data-testid="filter-group" :placeholder="zhCN.config.group" />
      <a-input v-model:value="filters.key" data-testid="filter-key" :placeholder="zhCN.config.key" />
      <a-button type="primary" data-testid="config-query" @click="load">{{ zhCN.common.query }}</a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" data-testid="config-table" :pagination="adminPagination(page, pageSize, total)" :row-key="adminRowKey" @change="onTableChange">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty">
<a-button v-auth="PERMS.CONFIG_CREATE" type="primary" size="small" @click="openCreate">
        {{ zhCN.common.create }}
      </a-button>
        </a-empty>
      </template>

      <a-table-column :title="zhCN.config.key">
        <template #default="{ record: row }">{{ row.configKey }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.config.group">
        <template #default="{ record: row }">{{ row.configGroup }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.config.value">
        <template #default="{ record: row }"><span data-testid="config-value">{{ row.configValue }}</span></template>
      </a-table-column>
      <a-table-column :title="zhCN.config.valueType">
        <template #default="{ record: row }">{{ row.valueType }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.config.masked">
        <template #default="{ record: row }">{{ row.masked ? "Y" : "N" }}</template>
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
            <a-button size="small" v-auth="PERMS.CONFIG_UPDATE" data-testid="config-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      :feedback="formOpen ? feedback : null"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <p v-if="editing?.masked" class="hint">{{ zhCN.config.keepValue }}</p>
      <a-form-item v-if="!editing" :label="zhCN.config.key">
        <a-input v-model:value="form.configKey" data-testid="config-key" required />
      </a-form-item>
      <a-form-item :label="zhCN.config.group">
        <a-input v-model:value="form.configGroup" data-testid="config-group" required />
      </a-form-item>
      <a-form-item :label="editing?.masked ? zhCN.config.newValue : zhCN.config.value">
        <a-input v-model:value="form.configValue" data-testid="config-value-input" :required="!editing" />
      </a-form-item>
      <a-form-item :label="zhCN.config.valueType">
        <a-select v-model:value="form.valueType" data-testid="config-value-type">
        <a-select-option v-for="item in CONFIG_VALUE_TYPES" :key="item" :value="item">{{ item }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.config.masked">
        <a-checkbox v-model:checked="form.masked" data-testid="config-masked" />
      </a-form-item>
      <a-form-item v-if="editing" :label="zhCN.common.status">
        <a-select v-model:value="form.status">
        <a-select-option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</a-select-option>
        <a-select-option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</a-select-option>
      </a-select>
      </a-form-item>
      <a-form-item :label="zhCN.common.remark">
        <a-input v-model:value="form.remark" />
      </a-form-item>
    </FormDialog>
  </section>
</template>
