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
    <h2>{{ zhCN.simulate.title }}</h2>
    <div class="admin-toolbar">
      <input v-model="form.userId" data-testid="simulate-user" :placeholder="zhCN.simulate.userId" />
      <input v-model="form.taskId" data-testid="simulate-task" :placeholder="zhCN.simulate.taskId" />
      <input v-model="form.instanceId" data-testid="simulate-instance" :placeholder="zhCN.simulate.instanceId" />
      <input v-model="form.stepCode" data-testid="simulate-step" :placeholder="zhCN.simulate.stepCode" />
      <input v-model="form.category" data-testid="simulate-category" :placeholder="zhCN.simulate.category" />
    </div>
    <div class="admin-toolbar">
      <button v-auth="PERMS.SIMULATE_TASK" type="button" data-testid="simulate-list" @click="loadList">
        {{ zhCN.simulate.list }}
      </button>
      <button v-auth="PERMS.SIMULATE_TASK" type="button" data-testid="simulate-detail" @click="loadDetail">
        {{ zhCN.simulate.detail }}
      </button>
      <button v-auth="PERMS.SIMULATE_TASK" type="button" data-testid="simulate-start" @click="startTask">
        {{ zhCN.simulate.start }}
      </button>
      <button v-auth="PERMS.SIMULATE_TASK" type="button" data-testid="simulate-click" @click="clickStep">
        {{ zhCN.simulate.click }}
      </button>
      <button v-auth="PERMS.SIMULATE_TASK" type="button" data-testid="simulate-callback" @click="callbackStep">
        {{ zhCN.simulate.callback }}
      </button>
      <button v-auth="PERMS.SIMULATE_TASK" type="button" data-testid="simulate-progress" @click="progressStep">
        {{ zhCN.simulate.progress }}
      </button>
      <button v-auth="PERMS.SIMULATE_FLOW" type="button" data-testid="simulate-flow" @click="runFlow">
        {{ zhCN.simulate.flow }}
      </button>
      <button v-auth="PERMS.SIMULATE_TASK" type="button" data-testid="simulate-reverse" @click="reverseRun">
        {{ zhCN.simulate.reverse }}
      </button>
    </div>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <p v-if="lastResult" data-testid="simulate-result">{{ lastResult }}</p>
    <p v-if="records.length === 0 && !loading" data-testid="page-empty">{{ zhCN.common.empty }}</p>
    <table v-else-if="records.length > 0" class="data-table" data-testid="simulate-table">
      <thead>
        <tr>
          <th>{{ zhCN.simulate.taskId }}</th>
          <th>{{ zhCN.simulate.taskCode }}</th>
          <th>{{ zhCN.common.status }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in records" :key="row.taskId">
          <td>{{ row.taskId }}</td>
          <td>{{ row.taskCode }}</td>
          <td>{{ row.userStatus }}</td>
        </tr>
      </tbody>
    </table>
    <table v-if="flowSteps.length > 0" class="data-table" data-testid="simulate-flow-table">
      <thead>
        <tr>
          <th>{{ zhCN.simulate.stepCode }}</th>
          <th>{{ zhCN.simulate.stepType }}</th>
          <th>{{ zhCN.simulate.action }}</th>
          <th>{{ zhCN.common.status }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in flowSteps" :key="`${row.stepCode}-${row.action}`">
          <td>{{ row.stepCode }}</td>
          <td>{{ row.type }}</td>
          <td>{{ row.action }}</td>
          <td>{{ row.stepStatus }}</td>
        </tr>
      </tbody>
    </table>
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
