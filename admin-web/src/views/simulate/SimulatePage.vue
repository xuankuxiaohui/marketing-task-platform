<template>
  <div class="simulate-page">
    <!-- Top Selection Bar -->
    <el-card class="selection-card">
      <div class="selection-bar">
        <div class="selection-group">
          <label class="selection-label">测试用户</label>
          <el-select
            v-model="selectedUserId"
            filterable
            allow-create
            placeholder="选择或输入 userId"
            @change="onUserChange"
            style="width: 200px"
          >
            <el-option
              v-for="u in testUsers"
              :key="u.userId"
              :label="u.userId"
              :value="u.userId"
            />
          </el-select>
        </div>

        <div class="selection-group">
          <label class="selection-label">省份</label>
          <el-select v-model="selectedProvince" placeholder="选择省份" style="width: 140px">
            <el-option
              v-for="p in provinces"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </div>

        <div class="selection-group">
          <label class="selection-label">平台</label>
          <el-select v-model="selectedPlatform" placeholder="选择平台" style="width: 120px">
            <el-option label="IOS" value="IOS" />
            <el-option label="ANDROID" value="ANDROID" />
            <el-option label="MINIAPP" value="MINIAPP" />
            <el-option label="WEB" value="WEB" />
          </el-select>
        </div>

        <div class="selection-group">
          <label class="selection-label">任务</label>
          <el-select
            v-model="selectedTaskId"
            filterable
            placeholder="选择已发布任务"
            style="width: 280px"
            :disabled="!selectedUserId"
          >
            <el-option
              v-for="t in publishedTasks"
              :key="t.id"
              :label="`${t.name} (${t.code})`"
              :value="t.id!"
            />
          </el-select>
        </div>

        <el-button
          type="primary"
          :disabled="!selectedUserId || !selectedTaskId"
          :loading="startingFlow"
          @click="startFlow"
          class="start-btn"
        >
          开始模拟
        </el-button>

        <el-button
          v-if="instanceId"
          @click="stopSimulation"
          plain
          type="warning"
        >
          退出模拟
        </el-button>
      </div>
    </el-card>

    <!-- Main Content Area -->
    <div v-if="instanceId" class="main-area">
      <!-- Left: Flowchart -->
      <div class="flowchart-panel">
        <div class="panel-header">
          <span class="panel-title">步骤流程</span>
          <el-tag
            v-if="instanceInfo"
            :type="instanceStatusType(instanceInfo.status)"
            size="small"
          >
            {{ instanceStatusLabel(instanceInfo.status) }}
          </el-tag>
        </div>

        <div class="flowchart">
          <div
            v-for="(step, i) in steps"
            :key="step.stepId"
            class="step-node-wrapper"
          >
            <!-- Connector line -->
            <div v-if="i > 0" class="step-connector"
              :class="{ completed: isStepCompleted(steps[i - 1]) }"
            />

            <div
              class="step-card"
              :class="{
                'current-step': step.isCurrentStep,
                'completed-step': isStepCompleted(step),
                'clickable-step': step.type === 'CLICK' && step.isCurrentStep,
              }"
            >
              <div class="step-left">
                <div class="step-badge" :class="stepTypeClass(step.type)">
                  <template v-if="isStepCompleted(step)">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                  </template>
                  <template v-else-if="step.isCurrentStep && step.type === 'REWARD'">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                  </template>
                  <template v-else>
                    {{ step.seq }}
                  </template>
                </div>
              </div>

              <div class="step-body">
                <div class="step-header">
                  <span class="step-name">{{ step.name }}</span>
                  <el-tag
                    :type="stepTypeTagType(step.type)"
                    size="small"
                    effect="plain"
                  >
                    {{ stepTypeLabel(step.type) }}
                  </el-tag>
                </div>

                <div v-if="step.description" class="step-desc">
                  {{ step.description }}
                </div>

                <!-- PROGRESS type: show progress bar -->
                <div v-if="step.type === 'PROGRESS'" class="step-progress-info">
                  <el-progress
                    :percentage="progressPercent(step)"
                    :status="isStepCompleted(step) ? 'success' : undefined"
                    :stroke-width="6"
                    style="width: 180px"
                  />
                  <span class="progress-text">
                    {{ step.progressValue || 0 }} / {{ step.targetValue || 100 }}
                  </span>
                </div>

                <!-- CALLBACK type: show event key -->
                <div v-if="step.type === 'CALLBACK' && step.callbackEventKey" class="step-callback-info">
                  <code class="event-key-code">{{ step.callbackEventKey }}</code>
                </div>

                <!-- Action area for current step -->
                <div v-if="step.isCurrentStep && !isStepCompleted(step)" class="step-action">
                  <template v-if="step.type === 'CLICK'">
                    <el-button
                      type="primary"
                      size="small"
                      :loading="actionLoading[step.stepId]"
                      @click="doClick(step)"
                    >
                      点击完成
                    </el-button>
                  </template>

                  <template v-else-if="step.type === 'CALLBACK'">
                    <div class="callback-form">
                      <el-input
                        v-model="callbackEvents[step.stepId]"
                        :placeholder="step.callbackEventKey || '输入 eventKey'"
                        size="small"
                        style="width: 180px"
                      />
                      <el-button
                        type="primary"
                        size="small"
                        :loading="actionLoading[step.stepId]"
                        @click="doCallback(step)"
                      >
                        发送回调
                      </el-button>
                    </div>
                  </template>

                  <template v-else-if="step.type === 'PROGRESS'">
                    <div class="progress-form">
                      <el-input-number
                        v-model="progressInputs[step.stepId]"
                        :min="0"
                        :max="step.targetValue"
                        size="small"
                        style="width: 120px"
                      />
                      <span class="progress-target">/ {{ step.targetValue }}</span>
                      <el-button
                        type="primary"
                        size="small"
                        :loading="actionLoading[step.stepId]"
                        @click="doProgress(step)"
                      >
                        上报进度
                      </el-button>
                    </div>
                  </template>

                  <template v-else-if="step.type === 'REWARD' || step.type === 'PASSIVE'">
                    <span class="step-auto-text">系统自动处理</span>
                  </template>
                </div>
              </div>

              <div class="step-right">
                <span v-if="isStepCompleted(step)" class="step-status done">已完成</span>
                <span v-else-if="step.isCurrentStep" class="step-status current">当前</span>
                <span v-else class="step-status pending">待完成</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Event Log + Actions -->
      <div class="side-panel">
        <!-- Event Log -->
        <el-card class="event-log-card">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">数据流日志</span>
              <el-button
                size="small"
                text
                @click="refreshEvents"
                :loading="loadingEvents"
              >
                刷新
              </el-button>
            </div>
          </template>

          <div class="event-log-scroll">
            <div v-if="events.length === 0" class="event-empty">
              暂无事件记录
            </div>
            <div
              v-for="evt in events"
              :key="evt.id"
              class="event-item"
            >
              <div class="event-time">{{ formatTime(evt.createdAt) }}</div>
              <div class="event-type">
                <el-tag :type="eventTypeTag(evt.eventType)" size="small" effect="dark">
                  {{ evt.eventType }}
                </el-tag>
              </div>
              <div class="event-detail">
                <template v-if="evt.eventData">
                  <code class="event-data-code">{{ evt.eventData }}</code>
                </template>
              </div>
            </div>
          </div>
        </el-card>

        <!-- Instance Info Card -->
        <el-card v-if="instanceInfo" class="info-card">
          <template #header><span class="panel-title">实例信息</span></template>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">实例 ID</span>
              <span class="info-value">{{ instanceInfo.id }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">用户 ID</span>
              <span class="info-value">{{ instanceInfo.userId }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">周期</span>
              <span class="info-value"><code>{{ instanceInfo.cycleKey }}</code></span>
            </div>
            <div class="info-item">
              <span class="info-label">当前步骤</span>
              <span class="info-value">第 {{ instanceInfo.currentStepSeq }} 步</span>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- Empty state -->
    <div v-if="!instanceId && !startingFlow" class="empty-state">
      <div class="empty-icon">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
      </div>
      <h3>开始模拟测试</h3>
      <p>选择测试用户和已发布的任务，点击"开始模拟"即可查看任务流程并逐步测试。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'SimulatePage' })
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getTestUsers,
  startSimulateFlow,
  getSimulateInstanceDetail,
  simulateClick,
  getSimulateInstanceEvents,
} from '../../api/simulate'
import { simulateCallback, simulateProgress } from '../../api/simulate'
import { listTasks } from '../../api/task'

// ---- constants ----
const provinces = [
  { label: '上海', value: 'SH' },
  { label: '北京', value: 'BJ' },
  { label: '广东', value: 'GD' },
  { label: '浙江', value: 'ZJ' },
  { label: '江苏', value: 'JS' },
  { label: '四川', value: 'SC' },
  { label: '湖北', value: 'HB' },
  { label: '湖南', value: 'HN' },
  { label: '山东', value: 'SD' },
  { label: '河南', value: 'HA' },
  { label: '福建', value: 'FJ' },
  { label: '安徽', value: 'AH' },
  { label: '河北', value: 'HE' },
  { label: '辽宁', value: 'LN' },
  { label: '陕西', value: 'SN' },
  { label: '重庆', value: 'CQ' },
  { label: '天津', value: 'TJ' },
  { label: '云南', value: 'YN' },
  { label: '贵州', value: 'GZ' },
  { label: '广西', value: 'GX' },
]

// ---- state ----
const testUsers = ref<{ userId: string }[]>([])
const publishedTasks = ref<{ id?: number; name: string; code: string }[]>([])

const selectedUserId = ref('')
const selectedProvince = ref('SH')
const selectedPlatform = ref('IOS')
const selectedTaskId = ref<number | null>(null)

const startingFlow = ref(false)
const instanceId = ref<number | null>(null)
const instanceInfo = ref<any>(null)
const steps = ref<any[]>([])

const events = ref<any[]>([])
const loadingEvents = ref(false)

const actionLoading = reactive<Record<number, boolean>>({})
const callbackEvents = reactive<Record<number, string>>({})
const progressInputs = reactive<Record<number, number>>({})

// ---- lifecycle ----
const route = useRoute()

onMounted(async () => {
  await Promise.all([loadTestUsers(), loadPublishedTasks()])
  const queryTaskId = route.query.taskId
  if (queryTaskId) {
    const id = Number(queryTaskId)
    if (publishedTasks.value.some(t => t.id === id)) {
      selectedTaskId.value = id
    }
  }
})

async function loadTestUsers() {
  try {
    const res = await getTestUsers()
    testUsers.value = (res as any).data?.data || []
  } catch (e) {
    console.error('Failed to load test users:', e)
  }
}

async function loadPublishedTasks() {
  try {
    const res = await listTasks()
    const records = (res as any).data?.data?.records || []
    publishedTasks.value = records.filter((t: any) => t.status === 'PUBLISHED')
  } catch (e) {
    console.error('Failed to load tasks:', e)
  }
}

function onUserChange() {
  // clear task selection when user changes
  selectedTaskId.value = null
}

// ---- flow control ----
async function startFlow() {
  if (!selectedUserId.value || !selectedTaskId.value) return

  startingFlow.value = true
  try {
    const res = await startSimulateFlow({
      userId: selectedUserId.value,
      taskId: selectedTaskId.value,
      province: selectedProvince.value,
      platform: selectedPlatform.value,
    })
    const detail = (res as any).data?.data
    applyDetail(detail)
    await refreshEvents()
    ElMessage.success('模拟已启动')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '启动模拟失败')
  } finally {
    startingFlow.value = false
  }
}

async function refreshDetail() {
  if (!instanceId.value) return
  try {
    const res = await getSimulateInstanceDetail(instanceId.value)
    const detail = (res as any).data?.data
    applyDetail(detail)
  } catch (e: any) {
    console.error('Failed to refresh detail:', e)
  }
}

function applyDetail(detail: any) {
  if (!detail) return
  instanceInfo.value = detail.instance
  instanceId.value = detail.instance?.id
  steps.value = detail.steps || []

  // Initialize progress inputs for PROGRESS steps
  for (const step of steps.value) {
    if (step.type === 'PROGRESS' && !(step.stepId in progressInputs)) {
      progressInputs[step.stepId] = step.targetValue || 100
    }
    if (step.type === 'CALLBACK' && !(step.stepId in callbackEvents)) {
      callbackEvents[step.stepId] = step.callbackEventKey || ''
    }
  }
}

async function refreshEvents() {
  if (!instanceId.value) return
  loadingEvents.value = true
  try {
    const res = await getSimulateInstanceEvents(instanceId.value)
    events.value = (res as any).data?.data || []
  } catch (e: any) {
    console.error('Failed to load events:', e)
  } finally {
    loadingEvents.value = false
  }
}

function stopSimulation() {
  instanceId.value = null
  instanceInfo.value = null
  steps.value = []
  events.value = []
  ElMessage.info('已退出模拟')
}

// ---- step actions ----
async function doClick(step: any) {
  if (!instanceId.value) return
  actionLoading[step.stepId] = true
  try {
    const res = await simulateClick(instanceId.value, step.stepId)
    applyDetail((res as any).data?.data)
    await refreshEvents()
    ElMessage.success(`步骤 "${step.name}" 执行成功`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '执行失败')
  } finally {
    actionLoading[step.stepId] = false
  }
}

async function doCallback(step: any) {
  if (!instanceId.value) return
  const eventKey = callbackEvents[step.stepId] || step.callbackEventKey
  if (!eventKey) {
    ElMessage.warning('请输入 eventKey')
    return
  }
  actionLoading[step.stepId] = true
  try {
    await simulateCallback(instanceId.value, eventKey)
    await refreshDetail()
    await refreshEvents()
    ElMessage.success(`回调 "${eventKey}" 发送成功`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '发送回调失败')
  } finally {
    actionLoading[step.stepId] = false
  }
}

async function doProgress(step: any) {
  if (!instanceId.value) return
  const value = progressInputs[step.stepId]
  if (value == null) {
    ElMessage.warning('请输入进度值')
    return
  }
  actionLoading[step.stepId] = true
  try {
    await simulateProgress(instanceId.value, step.stepId, value)
    await refreshDetail()
    await refreshEvents()
    ElMessage.success(`进度已上报: ${value}/${step.targetValue}`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上报进度失败')
  } finally {
    actionLoading[step.stepId] = false
  }
}

// ---- display helpers ----
function isStepCompleted(step: any): boolean {
  // Check if this seq is before the current step seq (already advanced past)
  if (step.seq < (instanceInfo.value?.currentStepSeq || 0)) return true
  // Check explicit progress status
  return step.progressStatus === 'COMPLETED'
}

function progressPercent(step: any): number {
  if (!step.targetValue) return 0
  return Math.round(((step.progressValue || 0) / step.targetValue) * 100)
}

function stepTypeLabel(type: string): string {
  const map: Record<string, string> = {
    CLICK: '点击',
    CALLBACK: '回调',
    PROGRESS: '进度',
    REWARD: '奖励',
    PASSIVE: '被动',
  }
  return map[type] || type
}

function stepTypeTagType(type: string): string {
  const map: Record<string, string> = {
    CLICK: '',
    CALLBACK: 'warning',
    PROGRESS: 'info',
    REWARD: 'success',
    PASSIVE: '',
  }
  return map[type] || ''
}

function stepTypeClass(type: string): string {
  const map: Record<string, string> = {
    CLICK: 'type-click',
    CALLBACK: 'type-callback',
    PROGRESS: 'type-progress',
    REWARD: 'type-reward',
    PASSIVE: 'type-passive',
  }
  return map[type] || ''
}

function instanceStatusLabel(s: string): string {
  const map: Record<string, string> = {
    PENDING: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    REWARDED: '已奖励',
    EXPIRED: '已过期',
  }
  return map[s] || s
}

function instanceStatusType(s: string): string {
  const map: Record<string, string> = {
    PENDING: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success',
    REWARDED: '',
    EXPIRED: 'danger',
  }
  return map[s] || 'info'
}

function eventTypeTag(type: string): string {
  const map: Record<string, string> = {
    INSTANCE_CREATED: 'success',
    STEP_COMPLETED: '',
    REWARD_TRIGGERED: 'warning',
    REWARD_SUCCESS: 'success',
    REWARD_FAILURE: 'danger',
    TASK_VIEWED: 'info',
    CLAIM_SUCCESS: '',
    FILTER_EVALUATED: 'info',
  }
  return map[type] || 'info'
}

function formatTime(t: string): string {
  if (!t) return ''
  return new Date(t).toLocaleTimeString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.simulate-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

/* Selection bar */
.selection-card {
  flex-shrink: 0;
}
.selection-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.selection-group {
  display: flex;
  align-items: center;
  gap: 6px;
}
.selection-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.start-btn {
  margin-left: auto;
}

/* Main area: two columns */
.main-area {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}

/* Flowchart panel */
.flowchart-panel {
  flex: 1;
  min-width: 0;
  background: var(--color-surface);
  border-radius: 8px;
  border: 1px solid var(--color-border);
  overflow-y: auto;
  padding: 20px;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary);
}

/* Flowchart */
.flowchart {
  margin-top: 16px;
  padding-left: 8px;
}
.step-node-wrapper {
  position: relative;
}
.step-connector {
  width: 2px;
  height: 24px;
  background: var(--color-border);
  margin-left: 19px;
}
.step-connector.completed {
  background: var(--color-success);
}

/* Step card */
.step-card {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 8px;
  border: 1.5px solid var(--color-border);
  background: var(--color-surface);
  transition: all 0.15s;
}
.step-card:hover {
  border-color: var(--color-text-disabled);
}
.step-card.current-step {
  border-color: var(--color-brand-secondary);
  background: var(--color-brand-primary-subtle);
  box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.2);
}
.step-card.completed-step {
  border-color: var(--color-emerald-subtle);
  background: var(--color-success-subtle);
}
.step-card.clickable-step {
  cursor: pointer;
  border-style: dashed;
  border-color: var(--color-brand-secondary);
}

.step-left {
  flex-shrink: 0;
  padding-top: 2px;
}
.step-badge {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-inverse);
  background: var(--color-text-disabled);
}
.step-card.current-step .step-badge {
  background: var(--color-brand-secondary);
}
.step-card.completed-step .step-badge {
  background: var(--color-success);
}
.step-badge.type-click { background: var(--color-brand-secondary); }
.step-badge.type-callback { background: var(--color-warning); }
.step-badge.type-progress { background: var(--color-brand-secondary); }
.step-badge.type-reward { background: var(--color-success); }
.step-badge.type-passive { background: var(--color-text-muted); }

/* Completed step overrides all type colors to green */
.step-card.completed-step .step-badge { background: var(--color-success); }

.step-body {
  flex: 1;
  min-width: 0;
}
.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.step-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.step-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
  line-height: 1.5;
}
.step-progress-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
.progress-text {
  font-size: 12px;
  color: var(--color-text-muted);
  font-family: 'SF Mono', 'Fira Code', monospace;
}
.step-callback-info {
  margin-top: 6px;
}
.event-key-code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  background: var(--color-amber-subtle);
  color: var(--color-amber-text);
  padding: 2px 8px;
  border-radius: 4px;
}
.step-action {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.callback-form {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-form {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-target {
  font-size: 12px;
  color: var(--color-text-muted);
  font-family: 'SF Mono', 'Fira Code', monospace;
}
.step-auto-text {
  font-size: 12px;
  color: var(--color-text-muted);
  font-style: italic;
}

.step-right {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  padding-top: 4px;
}
.step-status {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}
.step-status.done {
  color: var(--color-emerald-text);
  background: var(--color-emerald-subtle);
}
.step-status.current {
  color: var(--color-brand-primary-hover);
  background: var(--el-color-primary-light-8);
}
.step-status.pending {
  color: var(--color-text-disabled);
  background: var(--color-border-light);
}

/* Side panel */
.side-panel {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.event-log-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.event-log-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  padding: 12px;
  overflow: hidden;
}
.event-log-scroll {
  max-height: 400px;
  overflow-y: auto;
}

.event-empty {
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
  padding: 24px 0;
}

.event-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border-light);
}
.event-item:last-child {
  border-bottom: none;
}
.event-time {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: var(--color-text-muted);
  white-space: nowrap;
  flex-shrink: 0;
  min-width: 70px;
}
.event-type {
  flex-shrink: 0;
}
.event-detail {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.event-data-code {
  font-size: 11px;
  color: var(--color-text-muted);
  word-break: break-all;
}

.info-card {
  flex-shrink: 0;
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.info-label {
  font-size: 11px;
  color: var(--color-text-muted);
  text-transform: uppercase;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.info-value {
  font-size: 13px;
  color: var(--color-text-primary);
  font-weight: 500;
}
.info-value code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 11px;
  background: var(--color-border-light);
  padding: 1px 5px;
  border-radius: 3px;
}

/* Empty state */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--color-text-muted);
}
.empty-icon {
  color: var(--color-text-disabled);
  margin-bottom: 16px;
  opacity: 0.5;
}
.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-muted);
  margin: 0 0 8px;
}
.empty-state p {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
  text-align: center;
  max-width: 400px;
}
</style>
