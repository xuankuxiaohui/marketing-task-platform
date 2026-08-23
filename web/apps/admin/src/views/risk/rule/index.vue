<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { listRules, updateRule, type RiskRuleResponse } from "@/api/risk";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { hasAuth } from "@/directives/auth";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, writeOrFeedback, type PageFeedback } from "@/utils/feedback";
import { adminRowKey } from "@/utils/table";

defineOptions({ name: "RiskRulePage" });

const ACTIONS = ["REJECT", "SILENT_REJECT", "MARK"] as const;

const records = ref<RiskRuleResponse[]>([]);
const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const formOpen = ref(false);
const saving = ref(false);
const editing = ref<RiskRuleResponse | null>(null);
const form = reactive({
  enabled: true,
  threshold: 1,
  windowSeconds: "" as string,
  action: "REJECT" as (typeof ACTIONS)[number],
});

async function load(): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const result = await listRules();
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  records.value = parsed.data ?? [];
}

function openEdit(row: RiskRuleResponse): void {
  editing.value = row;
  form.enabled = Boolean(row.enabled);
  form.threshold = Number(row.threshold);
  form.windowSeconds = row.windowSeconds == null ? "" : String(row.windowSeconds);
  form.action = (ACTIONS.includes(row.action as (typeof ACTIONS)[number])
    ? row.action
    : "REJECT") as (typeof ACTIONS)[number];
  formOpen.value = true;
}

async function submit(): Promise<void> {
  if (!editing.value) {
    return;
  }
  saving.value = true;
  feedback.value = null;
  const windowSeconds = form.windowSeconds.trim() === "" ? null : Number(form.windowSeconds);
  const result = await updateRule(editing.value.ruleCode, {
    enabled: form.enabled,
    threshold: Number(form.threshold),
    windowSeconds,
    action: form.action,
  });
  saving.value = false;
  const parsed = writeOrFeedback(result);
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
  <section class="admin-page" data-testid="rule-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.rule.title }}</h2>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <a-table size="small" :loading="loading" :data-source="records" class="data-table admin-table" :pagination="false" :row-key="adminRowKey">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>
      <a-table-column title="code">
        <template #default="{ record: row }">{{ row.ruleCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.rule.enabled">
        <template #default="{ record: row }">
          <a-tag :color="row.enabled ? 'success' : 'default'" :class="row.enabled ? 'status-tag--on' : 'status-tag--off'">
            {{ row.enabled ? zhCN.common.enabled : zhCN.common.disabled }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="zhCN.rule.threshold">
        <template #default="{ record: row }">{{ row.threshold }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.rule.windowSeconds">
        <template #default="{ record: row }">{{ row.windowSeconds ?? "-" }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.rule.action">
        <template #default="{ record: row }">{{ row.action }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.actions" :width="240">
        <template #default="{ record: row }">
          <div class="row-actions">
            <a-button size="small" v-if="hasAuth(PERMS.RISK_RULE_CONFIG)" data-testid="rule-edit" @click="openEdit(row)">
              {{ zhCN.common.edit }}
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <FormDialog
      :visible="formOpen"
      :title="zhCN.common.edit"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <label>
        <span>{{ zhCN.rule.enabled }}</span>
        <a-checkbox v-model:checked="form.enabled" data-testid="rule-enabled" />
      </label>
      <label>
        <span>{{ zhCN.rule.threshold }}</span>
        <a-input v-model:value.number="form.threshold" data-testid="rule-threshold" required type="number" />
      </label>
      <label>
        <span>{{ zhCN.rule.windowSeconds }}</span>
        <a-input v-model:value="form.windowSeconds" data-testid="rule-window" type="number" />
      </label>
      <label>
        <span>{{ zhCN.rule.action }}</span>
        <a-select v-model:value="form.action" data-testid="rule-action">
        <a-select-option v-for="action in ACTIONS" :key="action" :value="action">{{ action }}</a-select-option>
      </a-select>
      </label>
    </FormDialog>
  </section>
</template>
