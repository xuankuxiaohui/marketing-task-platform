<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  getDefinition,
  pageMutexGroups,
  publishDefinition,
  resetRevision,
  saveDefinition,
  validateExpression,
  type MutexGroupResponse,
  type PublishResponse,
  type TaskActionCommand,
} from "@/api/task";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import FeedbackBanner from "@/components/FeedbackBanner.vue";
import { PERMS } from "@/constants/identity";
import { CYCLE_TYPES, EXPR_TYPES, GRAY_TYPES, STEP_TYPES } from "@/constants/task";
import { zhCN } from "@/locales/zh-CN";
import { okOrFeedback, type PageFeedback } from "@/utils/feedback";
import { formatPublishImpact, isPublishPreview } from "@/utils/publish-confirm";
import {
  edgesToTransitions,
  nextSeq,
  nodesToSteps,
  stepsToNodes,
  transitionsToEdges,
  type CanvasStepNode,
  type CanvasTransitionEdge,
} from "@/utils/task-canvas";

defineOptions({ name: "TaskDefinitionEditPage" });

const TaskCanvasPanel = defineAsyncComponent(() => import("./TaskCanvasPanel.vue"));

const route = useRoute();
const router = useRouter();
const feedback = ref<PageFeedback | null>(null);
const saving = ref(false);
const loading = ref(false);
const mutexGroups = ref<MutexGroupResponse[]>([]);
const nodes = ref<CanvasStepNode[]>([]);
const edges = ref<CanvasTransitionEdge[]>([]);
const selectedNodeId = ref("");
const selectedEdgeId = ref("");
const confirm = ref<{ message: string; run: () => Promise<void> } | null>(null);
const exprFeedback = ref("");

const form = reactive({
  id: undefined as number | undefined,
  code: "",
  name: "",
  description: "",
  category: "",
  iconUrl: "",
  badgeText: "",
  startTime: "",
  endTime: "",
  sortWeight: 0,
  status: "",
  version: 0,
  pendingRevision: false,
  cycleType: "NONE",
  cronExpr: "",
  specialStart: "",
  specialEnd: "",
  mutexGroupId: "",
  grayType: "NONE",
  grayRatio: 100,
  abGroup: "",
  grayCrowdId: "",
  grayExcludeCrowdId: "",
  filterExpr: "",
  allowCrowdIds: "",
  excludeCrowdIds: "",
});

const stepForm = reactive({
  code: "",
  name: "",
  type: "CLICK",
  progressTarget: 1,
  prizeId: "",
});

const edgeForm = reactive({
  fromStepCode: "",
  toStepCode: "",
  conditionExpr: "",
  priority: 0,
});

const exprForm = reactive({
  expression: "",
  type: "FILTER",
});

const selectedNode = computed(() => nodes.value.find((node) => node.id === selectedNodeId.value));
const selectedEdge = computed(() => edges.value.find((edge) => edge.id === selectedEdgeId.value));

function routeId(): number | undefined {
  const raw = route.params.id;
  const value = Array.isArray(raw) ? raw[0] : raw;
  if (!value) {
    return undefined;
  }
  const id = Number(value);
  return Number.isFinite(id) ? id : undefined;
}

function parseIdList(raw: string): number[] {
  return raw
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isFinite(item) && item > 0);
}

function buildSaveBody() {
  return {
    id: form.id,
    code: form.code,
    name: form.name,
    description: form.description || undefined,
    category: form.category || undefined,
    iconUrl: form.iconUrl || undefined,
    badgeText: form.badgeText || undefined,
    startTime: form.startTime || undefined,
    endTime: form.endTime || undefined,
    sortWeight: form.sortWeight,
    cycleType: form.cycleType,
    cronExpr: form.cronExpr || undefined,
    specialStart: form.specialStart || undefined,
    specialEnd: form.specialEnd || undefined,
    mutexGroupId: form.mutexGroupId ? Number(form.mutexGroupId) : undefined,
    gray: {
      type: form.grayType,
      ratio: form.grayType === "RATIO" ? form.grayRatio : undefined,
      abGroup: form.grayType === "AB" ? form.abGroup || undefined : undefined,
      crowdId: form.grayCrowdId ? Number(form.grayCrowdId) : undefined,
      excludeCrowdId: form.grayExcludeCrowdId ? Number(form.grayExcludeCrowdId) : undefined,
    },
    filter: {
      expr: form.filterExpr || undefined,
      allowCrowdIds: parseIdList(form.allowCrowdIds),
      excludeCrowdIds: parseIdList(form.excludeCrowdIds),
    },
    steps: nodesToSteps(nodes.value),
    transitions: edgesToTransitions(edges.value),
    actions: [] as TaskActionCommand[],
  };
}

async function loadMutex(): Promise<void> {
  const result = await pageMutexGroups({ page: 1, pageSize: 100 });
  const parsed = okOrFeedback(result);
  if (parsed.ok) {
    mutexGroups.value = parsed.data?.records ?? [];
  }
}

async function load(): Promise<void> {
  const id = routeId();
  if (id == null) {
    return;
  }
  loading.value = true;
  feedback.value = null;
  const result = await getDefinition(id);
  const parsed = okOrFeedback(result);
  loading.value = false;
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  const data = parsed.data;
  if (!data) {
    return;
  }
  form.id = data.id;
  form.code = data.code ?? "";
  form.name = data.name ?? "";
  form.description = data.description ?? "";
  form.category = data.category ?? "";
  form.iconUrl = data.iconUrl ?? "";
  form.badgeText = data.badgeText ?? "";
  form.startTime = data.startTime ?? "";
  form.endTime = data.endTime ?? "";
  form.sortWeight = data.sortWeight ?? 0;
  form.status = data.status ?? "";
  form.version = data.version ?? 0;
  form.pendingRevision = Boolean(data.pendingRevision);
  form.cycleType = data.cycleType ?? "NONE";
  form.cronExpr = data.cronExpr ?? "";
  form.specialStart = data.specialStart ?? "";
  form.specialEnd = data.specialEnd ?? "";
  form.mutexGroupId = data.mutexGroupId != null ? String(data.mutexGroupId) : "";
  form.grayType = data.gray?.type ?? "NONE";
  form.grayRatio = data.gray?.ratio ?? 100;
  form.abGroup = data.gray?.abGroup ?? "";
  form.grayCrowdId = data.gray?.crowdId != null ? String(data.gray.crowdId) : "";
  form.grayExcludeCrowdId = data.gray?.excludeCrowdId != null ? String(data.gray.excludeCrowdId) : "";
  form.filterExpr = data.filter?.expr ?? "";
  form.allowCrowdIds = (data.filter?.allowCrowdIds ?? []).join(",");
  form.excludeCrowdIds = (data.filter?.excludeCrowdIds ?? []).join(",");
  nodes.value = stepsToNodes(data.steps);
  edges.value = transitionsToEdges(data.transitions);
}

function addStep(): void {
  if (!stepForm.code || !stepForm.name) {
    return;
  }
  const seq = nextSeq(nodes.value);
  const next = stepsToNodes([
    ...nodesToSteps(nodes.value),
    {
      code: stepForm.code,
      name: stepForm.name,
      seq,
      type: stepForm.type,
      progressTarget: stepForm.type === "PROGRESS" ? Number(stepForm.progressTarget) : undefined,
      prizeId: stepForm.type === "REWARD" && stepForm.prizeId ? Number(stepForm.prizeId) : undefined,
    },
  ]);
  nodes.value = next;
  stepForm.code = "";
  stepForm.name = "";
}

function addEdge(): void {
  if (!edgeForm.fromStepCode || !edgeForm.toStepCode) {
    return;
  }
  edges.value = transitionsToEdges([
    ...edgesToTransitions(edges.value),
    {
      fromStepCode: edgeForm.fromStepCode,
      toStepCode: edgeForm.toStepCode,
      conditionExpr: edgeForm.conditionExpr || undefined,
      priority: Number(edgeForm.priority) || 0,
    },
  ]);
  edgeForm.fromStepCode = "";
  edgeForm.toStepCode = "";
  edgeForm.conditionExpr = "";
}

async function save(): Promise<void> {
  saving.value = true;
  feedback.value = null;
  const result = await saveDefinition(buildSaveBody());
  saving.value = false;
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  if (parsed.data?.id != null && form.id == null) {
    form.id = parsed.data.id;
    await router.replace(`/task/definitions/edit/${parsed.data.id}`);
  }
  form.version = parsed.data?.version ?? form.version;
  form.status = parsed.data?.status ?? form.status;
}

async function onPublish(): Promise<void> {
  if (form.id == null) {
    return;
  }
  feedback.value = null;
  const result = await publishDefinition(form.id, { confirm: false });
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  if (isPublishPreview(parsed.data)) {
    confirm.value = {
      message: formatPublishImpact(parsed.data as PublishResponse),
      run: async () => {
        const done = await publishDefinition(form.id as number, { confirm: true });
        const doneParsed = okOrFeedback(done);
        if (!doneParsed.ok) {
          feedback.value = doneParsed.feedback;
          return;
        }
        await load();
      },
    };
    return;
  }
  await load();
}

async function onResetRevision(): Promise<void> {
  if (form.id == null) {
    return;
  }
  const result = await resetRevision(form.id);
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  await load();
}

async function onValidate(): Promise<void> {
  exprFeedback.value = "";
  const result = await validateExpression({ expression: exprForm.expression, type: exprForm.type });
  const parsed = okOrFeedback(result);
  if (!parsed.ok) {
    feedback.value = parsed.feedback;
    return;
  }
  exprFeedback.value = parsed.data?.valid
    ? zhCN.task.validOk
    : (parsed.data?.error?.reason ?? "invalid");
}

async function onConfirm(): Promise<void> {
  const current = confirm.value;
  confirm.value = null;
  await current?.run();
}

onMounted(async () => {
  await loadMutex();
  await load();
});
</script>

<template>
  <section class="admin-page" data-testid="task-edit-page">
    <h2>{{ zhCN.task.editTitle }}</h2>
    <p v-if="form.pendingRevision" data-testid="pending-revision">{{ zhCN.task.pendingRevision }}</p>
    <FeedbackBanner :feedback="feedback" />
    <p v-if="loading" data-testid="page-loading">{{ zhCN.common.loading }}</p>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-button v-auth="form.id ? PERMS.TASK_DEF_UPDATE : PERMS.TASK_DEF_CREATE" data-testid="task-save" :disabled="saving" @click="save">
        {{ zhCN.common.save }}
      </el-button>
      <el-button v-if="form.id" v-auth="PERMS.TASK_DEF_PUBLISH" data-testid="task-publish" @click="onPublish">
        {{ zhCN.task.publish }}
      </el-button>
      <el-button v-if="form.pendingRevision" v-auth="PERMS.TASK_DEF_UPDATE" data-testid="task-reset-revision" @click="onResetRevision">
        {{ zhCN.task.resetRevision }}
      </el-button>
    </el-form>
    <div class="split-panels">
      <el-form-item :label="zhCN.task.code">
        <el-input v-model="form.code" data-testid="task-code" required />
      </el-form-item>
      <el-form-item :label="zhCN.task.name">
        <el-input v-model="form.name" data-testid="task-name" required />
      </el-form-item>
      <el-form-item :label="zhCN.task.category">
        <el-input v-model="form.category" data-testid="task-category" />
      </el-form-item>
      <el-form-item :label="zhCN.task.cycleType">
        <el-select v-model="form.cycleType" data-testid="task-cycle">
        <el-option v-for="item in CYCLE_TYPES" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.task.cronExpr">
        <el-input v-model="form.cronExpr" data-testid="task-cron" />
      </el-form-item>
      <el-form-item :label="zhCN.task.sortWeight">
        <el-input v-model.number="form.sortWeight" data-testid="task-weight" type="number" />
      </el-form-item>
      <el-form-item :label="zhCN.task.mutexGroup">
        <el-select v-model="form.mutexGroupId" data-testid="task-mutex">
        <el-option value="" label="—" />
        <el-option v-for="group in mutexGroups" :key="group.id" :value="String(group.id)" :label="group.name" />
      </el-select>
      </el-form-item>
      <el-form-item :label="zhCN.task.grayType">
        <el-select v-model="form.grayType" data-testid="task-gray">
        <el-option v-for="item in GRAY_TYPES" :key="item" :value="item" :label="item" />
      </el-select>
      </el-form-item>
    </div>
    <el-form-item :label="zhCN.task.filterExpr">
        <el-input v-model="form.filterExpr" data-testid="task-filter" />
      </el-form-item>
    <h3>{{ zhCN.task.canvas }}</h3>
    <ul data-testid="task-canvas-steps">
      <li v-for="node in nodes" :key="node.id">{{ node.data.code }} {{ node.data.type }}</li>
    </ul>
    <TaskCanvasPanel
      v-model:nodes="nodes"
      v-model:edges="edges"
      @select-node="selectedNodeId = $event"
      @select-edge="selectedEdgeId = $event"
    />
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="stepForm.code" data-testid="step-code" :placeholder="zhCN.task.stepCode" />
      <el-input v-model="stepForm.name" data-testid="step-name" :placeholder="zhCN.task.stepName" />
      <el-select v-model="stepForm.type" data-testid="step-type">
        <el-option v-for="item in STEP_TYPES" :key="item" :value="item" :label="item" />
      </el-select>
      <el-input v-if="stepForm.type === 'PROGRESS'" v-model.number="stepForm.progressTarget" data-testid="step-progress" type="number" />
      <el-input v-if="stepForm.type === 'REWARD'" v-model="stepForm.prizeId" data-testid="step-prize" :placeholder="zhCN.task.prizeId" />
      <el-button data-testid="step-add" @click="addStep">{{ zhCN.task.addStep }}</el-button>
    </el-form>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-input v-model="edgeForm.fromStepCode" data-testid="edge-from" :placeholder="zhCN.task.fromStep" />
      <el-input v-model="edgeForm.toStepCode" data-testid="edge-to" :placeholder="zhCN.task.toStep" />
      <el-input v-model="edgeForm.conditionExpr" data-testid="edge-cond" :placeholder="zhCN.task.condition" />
      <el-input v-model.number="edgeForm.priority" data-testid="edge-priority" type="number" />
      <el-button data-testid="edge-add" @click="addEdge">{{ zhCN.task.addEdge }}</el-button>
    </el-form>
    <p v-if="selectedNode" data-testid="selected-step">{{ selectedNode.data.code }} {{ selectedNode.data.type }}</p>
    <p v-if="selectedEdge" data-testid="selected-edge">{{ selectedEdge.source }} → {{ selectedEdge.target }}</p>
    <el-form :inline="true" class="admin-toolbar" @submit.prevent>
      <el-select v-model="exprForm.type" data-testid="expr-type">
        <el-option v-for="item in EXPR_TYPES" :key="item" :value="item" :label="item" />
      </el-select>
      <el-input v-model="exprForm.expression" data-testid="expr-input" :placeholder="zhCN.task.condition" />
      <el-button v-auth="PERMS.TASK_EXPR_VALIDATE" data-testid="expr-validate" @click="onValidate">
        {{ zhCN.task.validate }}
      </el-button>
      <span v-if="exprFeedback" data-testid="expr-result">{{ exprFeedback }}</span>
    </el-form>
    <ConfirmDialog
      :visible="confirm != null"
      :message="confirm?.message ?? ''"
      @confirm="onConfirm"
      @cancel="confirm = null"
    />
  </section>
</template>
