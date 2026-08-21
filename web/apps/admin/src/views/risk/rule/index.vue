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
    <table v-else class="admin-table">
      <thead>
        <tr>
          <th>code</th>
          <th>{{ zhCN.rule.enabled }}</th>
          <th>{{ zhCN.rule.threshold }}</th>
          <th>{{ zhCN.rule.windowSeconds }}</th>
          <th>{{ zhCN.rule.action }}</th>
          <th v-if="hasAuth(PERMS.RISK_RULE_CONFIG)">{{ zhCN.common.actions }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.ruleCode">
          <td>{{ row.ruleCode }}</td>
          <td>{{ row.enabled ? zhCN.common.enabled : zhCN.common.disabled }}</td>
          <td>{{ row.threshold }}</td>
          <td>{{ row.windowSeconds ?? "-" }}</td>
          <td>{{ row.action }}</td>
          <td v-if="hasAuth(PERMS.RISK_RULE_CONFIG)">
            <button type="button" data-testid="rule-edit" @click="openEdit(row)">{{ zhCN.common.edit }}</button>
          </td>
        </tr>
      </tbody>
    </table>
    <FormDialog
      :visible="formOpen"
      :title="zhCN.common.edit"
      :saving="saving"
      @submit="submit"
      @cancel="formOpen = false"
    >
      <label>
        <span>{{ zhCN.rule.enabled }}</span>
        <input v-model="form.enabled" data-testid="rule-enabled" type="checkbox" />
      </label>
      <label>
        <span>{{ zhCN.rule.threshold }}</span>
        <input v-model.number="form.threshold" data-testid="rule-threshold" type="number" required />
      </label>
      <label>
        <span>{{ zhCN.rule.windowSeconds }}</span>
        <input v-model="form.windowSeconds" data-testid="rule-window" type="number" />
      </label>
      <label>
        <span>{{ zhCN.rule.action }}</span>
        <select v-model="form.action" data-testid="rule-action">
          <option v-for="action in ACTIONS" :key="action" :value="action">{{ action }}</option>
        </select>
      </label>
    </FormDialog>
  </section>
</template>
