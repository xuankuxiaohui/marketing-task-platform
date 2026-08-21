<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { listRules, updateRule, type RiskRuleResponse } from "@/api/risk";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import FormDialog from "@/components/FormDialog.vue";
import { PERMS } from "@/constants/identity";
import { hasAuth } from "@/directives/auth";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";

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
  <section class="admin-page" data-testid="rule-page">
    <h2>{{ zhCN.rule.title }}</h2>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading">{{ zhCN.common.loading }}</p>
    <el-table v-else :data="records" class="data-table" stripe>
      <el-table-column label="code">
        <template #default="{ row }">{{ row.ruleCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.rule.enabled">
        <template #default="{ row }">{{ row.enabled ? zhCN.common.enabled : zhCN.common.disabled }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.rule.threshold">
        <template #default="{ row }">{{ row.threshold }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.rule.windowSeconds">
        <template #default="{ row }">{{ row.windowSeconds ?? "-" }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.rule.action">
        <template #default="{ row }">{{ row.action }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.actions" min-width="240">
        <template #default="{ row }">
          <el-button v-if="hasAuth(PERMS.RISK_RULE_CONFIG)" data-testid="rule-edit" @click="openEdit(row)">{{ zhCN.common.edit }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <FormDialog
      :visible="formOpen"
      :title="zhCN.common.edit"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <label>
        <span>{{ zhCN.rule.enabled }}</span>
        <el-checkbox v-model="form.enabled" data-testid="rule-enabled" />
      </label>
      <label>
        <span>{{ zhCN.rule.threshold }}</span>
        <el-input v-model.number="form.threshold" data-testid="rule-threshold" type="number" required />
      </label>
      <label>
        <span>{{ zhCN.rule.windowSeconds }}</span>
        <el-input v-model="form.windowSeconds" data-testid="rule-window" type="number" />
      </label>
      <label>
        <span>{{ zhCN.rule.action }}</span>
        <el-select v-model="form.action" data-testid="rule-action">
        <el-option v-for="action in ACTIONS" :key="action" :value="action" :label="action" />
      </el-select>
      </label>
    </FormDialog>
  </section>
</template>
