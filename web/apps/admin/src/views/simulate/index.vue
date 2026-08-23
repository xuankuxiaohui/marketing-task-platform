<script setup lang="ts">
import { reactive, ref } from "vue";
import {
  simulateCallback,
  simulateClick,
  simulateDetail,
  simulateFlow,
  simulateList,
  simulateProgress,
  simulateReverse,
  simulateStart,
  type SimulateFlowStepView,
  type SimulateTaskCard,
} from "@/api/simulate";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { PERMS } from "@/constants/identity";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { ADMIN_PAGE_SIZE, adminRowKey } from "@/utils/table";

defineOptions({ name: "SimulateTaskPage" });

const loading = ref(false);
const feedback = ref<PageFeedback | null>(null);
const records = ref<SimulateTaskCard[]>([]);
const flowSteps = ref<SimulateFlowStepView[]>([]);
const lastResult = ref("");
const form = reactive({
  userId: "",
  taskId: "",
  instanceId: "",
  stepCode: "",
  bizNo: "",
  value: "1",
  reportId: "sim-1",
  category: "",
});

function userId(): number {
  return Number(form.userId);
}

function taskId(): number {
  return Number(form.taskId);
}

function instanceId(): number {
  return Number(form.instanceId);
}

async function run<T>(
  action: () => Promise<{ ok: true; data: T | null | undefined } | { ok: false; feedback: PageFeedback }>,
  describe: (data: T) => string,
): Promise<void> {
  loading.value = true;
  feedback.value = null;
  const parsed = await action();
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  if (parsed.data == null) {
    return;
  }
  lastResult.value = describe(parsed.data);
}

async function loadList(): Promise<void> {
  await run(async () => okOrFeedback(await simulateList({ userId: userId(), category: form.category || undefined, page: 1, pageSize: ADMIN_PAGE_SIZE })), (data) => {
    records.value = data.records ?? [];
    return `${zhCN.simulate.list}: ${data.total ?? 0}`;
  });
}

async function loadDetail(): Promise<void> {
  await run(async () => okOrFeedback(await simulateDetail(userId(), taskId())), (data) => {
    if (data.instanceId) {
      form.instanceId = String(data.instanceId);
    }
    return `${zhCN.simulate.detail}: ${data.status}`;
  });
}

async function startTask(): Promise<void> {
  await run(async () => okOrFeedback(await simulateStart(userId(), taskId())), (data) => {
    form.instanceId = String(data.instanceId);
    return `${zhCN.simulate.start}: ${data.instanceId} ${data.instanceStatus}`;
  });
}

async function clickStep(): Promise<void> {
  await run(async () => okOrFeedback(await simulateClick(userId(), instanceId(), form.stepCode)), (data) => {
    return `${zhCN.simulate.click}: ${data.stepStatus} / ${data.instanceStatus}`;
  });
}

async function callbackStep(): Promise<void> {
  await run(async () => okOrFeedback(await simulateCallback(userId(), instanceId(), form.stepCode, form.bizNo || undefined)), (data) => {
    return `${zhCN.simulate.callback}: ${data.stepStatus} / ${data.instanceStatus}`;
  });
}

async function progressStep(): Promise<void> {
  await run(async () => okOrFeedback(await simulateProgress(userId(), instanceId(), form.stepCode, Number(form.value), form.reportId)), (data) => {
    return `${zhCN.simulate.progress}: ${data.progressCurrent}/${data.progressTarget ?? "-"} ${data.stepStatus}`;
  });
}

async function runFlow(): Promise<void> {
  await run(async () => okOrFeedback(await simulateFlow(userId(), taskId())), (data) => {
    form.instanceId = String(data.instanceId);
    flowSteps.value = data.steps ?? [];
    return `${zhCN.simulate.flow}: ${data.instanceId} ${data.instanceStatus}`;
  });
}

async function reverseRun(): Promise<void> {
  await run(async () => okOrFeedback(await simulateReverse(instanceId())), (data) => {
    return `${zhCN.simulate.reverse}: stock=${data.stockRestored} points=${data.pointsReversed} sending=${data.sendingMarked} channel=${data.channelRevoked}`;
  });
}
</script>

<template>
  <section class="admin-page" data-testid="simulate-page">
    <div class="admin-page__header">
      <h2>{{ zhCN.simulate.title }}</h2>
    </div>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-input v-model:value="form.userId" data-testid="simulate-user" :placeholder="zhCN.simulate.userId" />
      <a-input v-model:value="form.taskId" data-testid="simulate-task" :placeholder="zhCN.simulate.taskId" />
      <a-input v-model:value="form.instanceId" data-testid="simulate-instance" :placeholder="zhCN.simulate.instanceId" />
      <a-input v-model:value="form.stepCode" data-testid="simulate-step" :placeholder="zhCN.simulate.stepCode" />
      <a-input v-model:value="form.category" data-testid="simulate-category" :placeholder="zhCN.simulate.category" />
    </a-form>
    <a-form layout="inline" class="admin-toolbar" @submit.prevent>
      <a-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-list" @click="loadList">
        {{ zhCN.simulate.list }}
      </a-button>
      <a-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-detail" @click="loadDetail">
        {{ zhCN.simulate.detail }}
      </a-button>
      <a-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-start" @click="startTask">
        {{ zhCN.simulate.start }}
      </a-button>
      <a-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-click" @click="clickStep">
        {{ zhCN.simulate.click }}
      </a-button>
      <a-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-callback" @click="callbackStep">
        {{ zhCN.simulate.callback }}
      </a-button>
      <a-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-progress" @click="progressStep">
        {{ zhCN.simulate.progress }}
      </a-button>
      <a-button v-auth="PERMS.SIMULATE_FLOW" data-testid="simulate-flow" @click="runFlow">
        {{ zhCN.simulate.flow }}
      </a-button>
      <a-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-reverse" @click="reverseRun">
        {{ zhCN.simulate.reverse }}
      </a-button>
    </a-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="lastResult" data-testid="simulate-result">{{ lastResult }}</p>
    <a-table size="small" :loading="loading" :data-source="records" class="data-table" data-testid="simulate-table" :pagination="false" :row-key="adminRowKey">
      <template #emptyText>
        <a-empty :description="zhCN.common.empty" data-testid="page-empty" />
      </template>

      <a-table-column :title="zhCN.simulate.taskId">
        <template #default="{ record: row }">{{ row.taskId }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.simulate.taskCode">
        <template #default="{ record: row }">{{ row.taskCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">{{ row.userStatus }}</template>
      </a-table-column>
    </a-table>
    <a-table size="small" v-if="flowSteps.length > 0" :data-source="flowSteps" class="data-table" data-testid="simulate-flow-table" :pagination="false" :row-key="adminRowKey">
      <a-table-column :title="zhCN.simulate.stepCode">
        <template #default="{ record: row }">{{ row.stepCode }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.simulate.stepType">
        <template #default="{ record: row }">{{ row.type }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.simulate.action">
        <template #default="{ record: row }">{{ row.action }}</template>
      </a-table-column>
      <a-table-column :title="zhCN.common.status">
        <template #default="{ record: row }">{{ row.stepStatus }}</template>
      </a-table-column>
    </a-table>
  </section>
</template>

<style scoped>
.admin-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
