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
  await run(async () => okOrFeedback(await simulateList({ userId: userId(), category: form.category || undefined, page: 1, pageSize: 20 })), (data) => {
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
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="form.userId" data-testid="simulate-user" :placeholder="zhCN.simulate.userId" />
      <el-input v-model="form.taskId" data-testid="simulate-task" :placeholder="zhCN.simulate.taskId" />
      <el-input v-model="form.instanceId" data-testid="simulate-instance" :placeholder="zhCN.simulate.instanceId" />
      <el-input v-model="form.stepCode" data-testid="simulate-step" :placeholder="zhCN.simulate.stepCode" />
      <el-input v-model="form.category" data-testid="simulate-category" :placeholder="zhCN.simulate.category" />
    </el-form>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-list" @click="loadList">
        {{ zhCN.simulate.list }}
      </el-button>
      <el-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-detail" @click="loadDetail">
        {{ zhCN.simulate.detail }}
      </el-button>
      <el-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-start" @click="startTask">
        {{ zhCN.simulate.start }}
      </el-button>
      <el-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-click" @click="clickStep">
        {{ zhCN.simulate.click }}
      </el-button>
      <el-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-callback" @click="callbackStep">
        {{ zhCN.simulate.callback }}
      </el-button>
      <el-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-progress" @click="progressStep">
        {{ zhCN.simulate.progress }}
      </el-button>
      <el-button v-auth="PERMS.SIMULATE_FLOW" data-testid="simulate-flow" @click="runFlow">
        {{ zhCN.simulate.flow }}
      </el-button>
      <el-button v-auth="PERMS.SIMULATE_TASK" data-testid="simulate-reverse" @click="reverseRun">
        {{ zhCN.simulate.reverse }}
      </el-button>
    </el-form>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-if="lastResult" data-testid="simulate-result">{{ lastResult }}</p>
    <p v-if="records.length === 0 && !loading" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <el-table v-else-if="records.length > 0" :data="records" class="data-table" data-testid="simulate-table" stripe>
      <el-table-column :label="zhCN.simulate.taskId">
        <template #default="{ row }">{{ row.taskId }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.simulate.taskCode">
        <template #default="{ row }">{{ row.taskCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">{{ row.userStatus }}</template>
      </el-table-column>
    </el-table>
    <el-table v-if="flowSteps.length > 0" :data="flowSteps" class="data-table" data-testid="simulate-flow-table" stripe>
      <el-table-column :label="zhCN.simulate.stepCode">
        <template #default="{ row }">{{ row.stepCode }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.simulate.stepType">
        <template #default="{ row }">{{ row.type }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.simulate.action">
        <template #default="{ row }">{{ row.action }}</template>
      </el-table-column>
      <el-table-column :label="zhCN.common.status">
        <template #default="{ row }">{{ row.stepStatus }}</template>
      </el-table-column>
    </el-table>
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
