<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { createConfig, pageConfigs, updateConfig, type ConfigView } from "@/api/system";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { CONFIG_VALUE_TYPES, PERMS, STATUS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { adminStatusLabel } from "@/utils/status-label";
import { buildConfigUpdateBody, CONFIG_MASK_DISPLAY } from "@/utils/config-update";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

defineOptions({ name: "ConfigManagePage" });

const records = ref<ConfigView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
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
    const parsed = okOrFeedback(result);
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
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  formOpen.value = false;
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="admin-page" data-testid="config-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.config.title }}</h2>
      <el-button type="primary" v-auth="PERMS.CONFIG_CREATE" data-testid="config-create" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="filters.configGroup" data-testid="filter-group" :placeholder="zhCN.config.group" />
      <el-input v-model="filters.key" data-testid="filter-key" :placeholder="zhCN.config.key" />
      <el-button data-testid="config-query" @click="load">{{ zhCN.common.query }}</el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <div v-else-if="records.length === 0" data-testid="page-empty" class="page-empty">
      <span>{{ zhCN.common.empty }}</span>
      <el-button v-auth="PERMS.CONFIG_CREATE" text type="primary" @click="openCreate">
        {{ zhCN.common.create }}
      </el-button>
    </div>
    <el-table v-else :data="records" class="data-table admin-table" data-testid="config-table" size="small" stripe>
      <el-table-column :label="zhCN.config.key">
        <template #default="{ row }">{{ row.configKey }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.config.group">
        <template #default="{ row }">{{ row.configGroup }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.config.value">
        <template #default="{ row }"><span data-testid="config-value">{{ row.configValue }}</span></template>
      </el-table-column>
      <el-table-column :label="zhCN.config.valueType">
        <template #default="{ row }">{{ row.valueType }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.config.masked">
        <template #default="{ row }">{{ row.masked ? "Y" : "N" }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'success' : 'info'"
            :class="row.status === 'ENABLED' || row.status === 'PUBLISHED' || row.status === 'SCHEDULED' ? 'status-tag--on' : 'status-tag--off'"
          >
            {{ adminStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button text v-auth="PERMS.CONFIG_UPDATE" data-testid="config-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span>{{ zhCN.common.total }} {{ total }}</span>
      <el-button :disabled="page <= 1" @click="page -= 1; load()">{{ zhCN.common.prevPage }}</el-button>
      <span>{{ page }}</span>
      <el-button :disabled="page * pageSize >= total" @click="page += 1; load()">{{ zhCN.common.nextPage }}</el-button>
    </div>
    <FormDialog
      :visible="formOpen"
      :title="editing ? zhCN.common.edit : zhCN.common.create"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <p v-if="editing?.masked" class="hint">{{ zhCN.config.keepValue }}</p>
      <el-form-item v-if="!editing" :label="zhCN.config.key">
        <el-input v-model="form.configKey" data-testid="config-key" required />
      </el-form-item>
      <el-form-item :label="zhCN.config.group">
        <el-input v-model="form.configGroup" data-testid="config-group" required />
      </el-form-item>
      <el-form-item :label="editing?.masked ? zhCN.config.newValue : zhCN.config.value">
        <el-input v-model="form.configValue" data-testid="config-value-input" :required="!editing" />
      </el-form-item>
      <el-form-item :label="zhCN.config.valueType">
        <el-select v-model="form.valueType" data-testid="config-value-type">
        <el-option v-for="item in CONFIG_VALUE_TYPES" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.config.masked">
        <el-checkbox v-model="form.masked" data-testid="config-masked" />
      </el-form-item>
      <el-form-item v-if="editing" :label="zhCN.common.status">
        <el-select v-model="form.status">
        <el-option :value="STATUS.ENABLED" :label="zhCN.common.enabled" />
        <el-option :value="STATUS.DISABLED" :label="zhCN.common.disabled" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.common.remark">
        <el-input v-model="form.remark" />
      </el-form-item>
    </FormDialog>
  </section>
</template>
