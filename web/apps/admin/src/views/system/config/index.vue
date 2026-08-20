<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { createConfig, pageConfigs, updateConfig, type ConfigView } from "@/api/system";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { CONFIG_VALUE_TYPES, PERMS, STATUS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
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
    <h2>{{ zhCN.config.title }}</h2>
    <div class="admin-toolbar">
      <input v-model="filters.configGroup" data-testid="filter-group" :placeholder="zhCN.config.group" />
      <input v-model="filters.key" data-testid="filter-key" :placeholder="zhCN.config.key" />
      <button type="button" data-testid="config-query" @click="load">{{ zhCN.common.query }}</button>
      <button v-auth="PERMS.CONFIG_CREATE" type="button" data-testid="config-create" @click="openCreate">
        {{ zhCN.common.create }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-else-if="records.length === 0" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else class="data-table" data-testid="config-table">
      <thead>
        <tr>
          <th>{{ zhCN.config.key }}</th>
          <th>{{ zhCN.config.group }}</th>
          <th>{{ zhCN.config.value }}</th>
          <th>{{ zhCN.config.valueType }}</th>
          <th>{{ zhCN.config.masked }}</th>
          <th>{{ zhCN.common.status }}</th>
          <th>{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.configKey">
          <td>{{ row.configKey }}</td>
          <td>{{ row.configGroup }}</td>
          <td data-testid="config-value">{{ row.configValue }}</td>
          <td>{{ row.valueType }}</td>
          <td>{{ row.masked ? "Y" : "N" }}</td>
          <td>{{ row.status }}</td>
          <td class="row-actions">
            <button v-auth="PERMS.CONFIG_UPDATE" type="button" data-testid="config-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
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
      <p v-if="editing?.masked" class="hint">{{ zhCN.config.keepValue }}</p>
      <label v-if="!editing" class="field">
        <span>{{ zhCN.config.key }}</span>
        <input v-model="form.configKey" data-testid="config-key" required />
      </label>
      <label class="field">
        <span>{{ zhCN.config.group }}</span>
        <input v-model="form.configGroup" data-testid="config-group" required />
      </label>
      <label class="field">
        <span>{{ editing?.masked ? zhCN.config.newValue : zhCN.config.value }}</span>
        <input v-model="form.configValue" data-testid="config-value-input" :required="!editing" />
      </label>
      <label class="field">
        <span>{{ zhCN.config.valueType }}</span>
        <select v-model="form.valueType" data-testid="config-value-type">
          <option v-for="item in CONFIG_VALUE_TYPES" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="field">
        <span>
          <input v-model="form.masked" type="checkbox" data-testid="config-masked" />
          {{ zhCN.config.masked }}
        </span>
      </label>
      <label v-if="editing" class="field">
        <span>{{ zhCN.common.status }}</span>
        <select v-model="form.status">
          <option :value="STATUS.ENABLED">{{ zhCN.common.enabled }}</option>
          <option :value="STATUS.DISABLED">{{ zhCN.common.disabled }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ zhCN.common.remark }}</span>
        <input v-model="form.remark" />
      </label>
    </FormDialog>
  </section>
</template>
