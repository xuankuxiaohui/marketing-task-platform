<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">用户任务实例</span>
          <p class="page-sub">查询和管理用户的任务执行状态，支持按用户、任务、状态、时间筛选</p>
        </div>
      </div>
    </template>

    <!-- Filter Bar -->
    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="用户 ID">
        <el-input v-model="filters.userId" placeholder="输入用户 ID（模糊匹配）" clearable
          style="width:200px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="任务">
        <el-select v-model="filters.taskId" placeholder="选择任务" clearable filterable style="width:220px">
          <el-option v-for="t in taskOptions" :key="t.id" :label="`#${t.id} ${t.name}`" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" placeholder="选择状态" clearable style="width:140px">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
          start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD"
          style="width:240px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          查询
        </el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- Table -->
    <el-table :data="rows" highlight-current-row @row-click="showDetail"
      v-loading="loading" element-loading-text="加载中...">
      <el-table-column prop="id" label="ID" width="80" sortable />
      <el-table-column prop="userId" label="用户 ID" min-width="130" show-overflow-tooltip />
      <el-table-column label="任务" min-width="160">
        <template #default="{ row }">
          <template v-if="row.taskName">
            <span class="task-name-cell">{{ row.taskName }}</span>
            <span class="task-id-sub">#{{ row.taskId }}</span>
          </template>
          <span v-else class="task-id-only">任务 #{{ row.taskId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <span :class="['status-pill', instanceStatusClass(row.status)]">{{ instanceStatusLabel(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="当前步骤" width="100" align="center">
        <template #default="{ row }">
          <span class="step-badge">{{ row.currentStepSeq }}</span>
        </template>
      </el-table-column>
      <el-table-column label="周期" min-width="130">
        <template #default="{ row }">
          <code class="cycle-code">{{ row.cycleKey }}</code>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" width="170">
        <template #default="{ row }">
          <span v-if="row.completeTime" class="time-cell">{{ formatTime(row.completeTime) }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="发奖时间" width="170">
        <template #default="{ row }">
          <span v-if="row.rewardTime" class="time-cell reward-time">{{ formatTime(row.rewardTime) }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right" align="center">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click.stop="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
    <div class="pagination-wrap" v-if="total > 0">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>

    <!-- Detail Drawer -->
    <el-drawer v-model="drawerVisible" :title="`实例详情 #${detail?.instance?.id || ''}`"
      size="520px" direction="rtl">
      <template v-if="detail">
        <!-- Instance Info -->
        <div class="detail-section">
          <h3 class="detail-section-title">基本信息</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="实例 ID">{{ detail.instance.id }}</el-descriptions-item>
            <el-descriptions-item label="用户 ID">{{ detail.instance.userId }}</el-descriptions-item>
            <el-descriptions-item label="任务名称">
              <template v-if="detail.instance.taskName">
                <span class="drawer-task-link" @click="$router.push(`/tasks/${detail.instance.taskId}`)">
                  {{ detail.instance.taskName }}
                </span>
              </template>
              <span v-else>任务 #{{ detail.instance.taskId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="任务版本">v{{ detail.instance.taskVersion }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <span :class="['status-pill', instanceStatusClass(detail.instance.status)]">
                {{ instanceStatusLabel(detail.instance.status) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="周期">{{ detail.instance.cycleKey }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.instance.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(detail.instance.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ detail.instance.completeTime ? formatTime(detail.instance.completeTime) : '--' }}</el-descriptions-item>
            <el-descriptions-item label="发奖时间">{{ detail.instance.rewardTime ? formatTime(detail.instance.rewardTime) : '--' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- Step Timeline -->
        <div class="detail-section">
          <h3 class="detail-section-title">
            步骤进度
            <span class="step-count-badge">{{ completedSteps }}/{{ detail.totalSteps }}</span>
          </h3>
          <div class="step-timeline">
            <div v-for="(step, idx) in detail.steps" :key="step.stepId"
              :class="['step-item', stepProgressClass(step.status)]">
              <!-- Step connector line -->
              <div class="step-connector">
                <div :class="['step-dot', stepProgressClass(step.status)]">
                  <svg v-if="step.status === 'COMPLETED'" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                  <span v-else class="step-num">{{ step.stepSeq }}</span>
                </div>
                <div v-if="idx < detail.steps.length - 1" :class="['step-line', stepProgressClass(step.status)]" />
              </div>
              <!-- Step content -->
              <div class="step-content">
                <div class="step-header">
                  <span class="step-name">{{ step.stepName }}</span>
                  <span :class="['step-type-tag', stepTypeClass(step.stepType)]">{{ stepTypeLabel(step.stepType) }}</span>
                  <span :class="['step-status-tag', stepProgressClass(step.status)]">{{ stepProgressLabel(step.status) }}</span>
                </div>
                <div class="step-body">
                  <template v-if="step.stepDescription">
                    <p class="step-desc">{{ step.stepDescription }}</p>
                  </template>
                  <template v-if="step.stepType === 'PROGRESS' && step.targetValue">
                    <div class="progress-bar-wrap">
                      <div class="progress-bar">
                        <div class="progress-fill" :style="{ width: progressPercent(step) + '%' }" />
                      </div>
                      <span class="progress-text">{{ step.progressValue ?? 0 }} / {{ step.targetValue }}</span>
                    </div>
                  </template>
                  <template v-if="step.completeTime">
                    <span class="step-time">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" style="margin-right:3px;vertical-align:-2px"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                      {{ formatTime(step.completeTime) }}
                    </span>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="detail-actions">
          <el-button type="primary" plain @click="$router.push(`/tasks/${detail.instance.taskId}`)">
            查看任务定义
          </el-button>
          <el-button @click="drawerVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listInstances, getInstanceDetail, type InstanceVO, type InstanceDetail, type InstanceQueryParams } from '../../api/instance'
import { listTasks } from '../../api/task'

// --- State ---
const rows = ref<InstanceVO[]>([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({ page: 1, size: 20 })
const filters = reactive<InstanceQueryParams>({})
const dateRange = ref<[string, string] | null>(null)

const drawerVisible = ref(false)
const detail = ref<InstanceDetail | null>(null)
const detailLoading = ref(false)

const taskOptions = ref<Array<{ id: number; name: string }>>([])

// --- Computed ---
const completedSteps = computed(() =>
  detail.value ? detail.value.steps.filter(s => s.status === 'COMPLETED').length : 0
)

// --- Display helpers ---
const instanceStatusLabel = (s: string) => ({
  PENDING: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成',
  REWARDED: '已奖励', EXPIRED: '已过期',
}[s] || s)

const instanceStatusClass = (s: string) => ({
  PENDING: 'pending', IN_PROGRESS: 'in-progress', COMPLETED: 'completed',
  REWARDED: 'rewarded', EXPIRED: 'expired',
}[s] || '')

const statusOptions = [
  { value: 'PENDING', label: '未开始' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REWARDED', label: '已奖励' },
  { value: 'EXPIRED', label: '已过期' },
]

const stepTypeLabel = (t: string) => ({
  CLICK: '点击', CALLBACK: '回调', PROGRESS: '进度',
  REWARD: '奖励', PASSIVE: '被动',
}[t] || t)

const stepTypeClass = (t: string) => ({
  CLICK: 'type-click', CALLBACK: 'type-callback', PROGRESS: 'type-progress',
  REWARD: 'type-reward', PASSIVE: 'type-passive',
}[t] || '')

const stepProgressLabel = (s: string) => ({
  PENDING: '待完成', IN_PROGRESS: '进行中', COMPLETED: '已完成',
}[s] || s)

const stepProgressClass = (s: string) => ({
  PENDING: 'step-pending', IN_PROGRESS: 'step-in-progress', COMPLETED: 'step-completed',
}[s] || 'step-pending')

const progressPercent = (step: { progressValue: number | null; targetValue: number | null }) => {
  if (!step.targetValue || step.targetValue === 0) return 0
  return Math.min(Math.round(((step.progressValue ?? 0) / step.targetValue) * 100), 100)
}

const formatTime = (t: string | null | undefined) => {
  if (!t) return '--'
  return t.replace('T', ' ').substring(0, 19)
}

// --- Data loading ---
async function loadTasks() {
  try {
    const { data } = await listTasks()
    taskOptions.value = (data.data.records || []).map((t: any) => ({ id: t.id, name: t.name }))
  } catch (e) {
    console.error('Failed to load tasks:', e)
  }
}

async function load() {
  loading.value = true
  try {
    const params: InstanceQueryParams = {
      page: pagination.page,
      size: pagination.size,
      ...filters,
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    // Clean undefined keys
    Object.keys(params).forEach(k => {
      const key = k as keyof InstanceQueryParams
      if (params[key] === undefined || params[key] === '' || params[key] === null) {
        delete params[key]
      }
    })

    const { data } = await listInstances(params)
    rows.value = data.data.records || []
    total.value = data.data.total || 0
  } catch (e) {
    console.error('Failed to load instances:', e)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  load()
}

function reset() {
  filters.userId = undefined
  filters.taskId = undefined
  filters.status = undefined
  dateRange.value = null
  filters.startDate = undefined
  filters.endDate = undefined
  pagination.page = 1
  load()
}

function onPageChange() {
  load()
}

function onSizeChange() {
  pagination.page = 1
  load()
}

async function showDetail(row: InstanceVO) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const { data } = await getInstanceDetail(row.id)
    detail.value = data.data
  } catch (e) {
    console.error('Failed to load instance detail:', e)
  } finally {
    detailLoading.value = false
  }
}

// --- Init ---
onMounted(() => {
  loadTasks()
  load()
})
</script>

<style scoped>
/* Header */
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.page-title {
  font-size: 16px;
  font-weight: 700;
  color: #2d1b69;
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #a78bfa;
}

/* Filter Bar */
.filter-bar {
  margin-bottom: 16px;
  padding: 16px;
  background: #faf9ff;
  border-radius: 8px;
  border: 1px solid #ede9fe;
}
.filter-bar :deep(.el-form-item) {
  margin-bottom: 8px;
}

/* Table cells */
.task-name-cell {
  font-weight: 600;
  color: #2d1b69;
  margin-right: 6px;
}
.task-id-sub {
  font-size: 11px;
  color: #a78bfa;
}
.task-id-only {
  color: #6d28d9;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
}
.time-cell {
  font-size: 12px;
  color: #374151;
}
.reward-time {
  color: #7c3aed;
  font-weight: 500;
}
.empty-cell {
  color: #d4d4d8;
  font-size: 12px;
}
.cycle-code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 11px;
  background: #f5f3ff;
  color: #6d28d9;
  padding: 2px 6px;
  border-radius: 4px;
}

/* Status pills */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.6;
}
.status-pill::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.status-pill.pending     { background: #fef3c7; color: #b45309; }
.status-pill.pending::before     { background: #f59e0b; }
.status-pill.in-progress { background: #dbeafe; color: #1d4ed8; }
.status-pill.in-progress::before { background: #3b82f6; }
.status-pill.completed   { background: #d1fae5; color: #047857; }
.status-pill.completed::before   { background: #10b981; }
.status-pill.rewarded    { background: #ede9fe; color: #6d28d9; }
.status-pill.rewarded::before    { background: #7c3aed; }
.status-pill.expired     { background: #f1f5f9; color: #64748b; }
.status-pill.expired::before     { background: #94a3b8; }

.step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
}

/* Pagination */
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* --- Detail Drawer --- */
.detail-section {
  margin-bottom: 24px;
}
.detail-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #2d1b69;
  margin: 0 0 12px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.step-count-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  background: #ede9fe;
  color: #6d28d9;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

/* Step Timeline */
.step-timeline {
  padding: 4px 0;
}
.step-item {
  display: flex;
  gap: 12px;
  min-height: 64px;
}
.step-item:last-child {
  min-height: auto;
}
.step-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 28px;
  flex-shrink: 0;
}
.step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
  transition: all 0.2s;
}
.step-dot.step-pending {
  background: #f1f5f9;
  color: #94a3b8;
  border: 2px solid #e2e8f0;
}
.step-dot.step-in-progress {
  background: #dbeafe;
  color: #1d4ed8;
  border: 2px solid #3b82f6;
}
.step-dot.step-completed {
  background: #d1fae5;
  color: #047857;
  border: 2px solid #10b981;
}
.step-num {
  line-height: 1;
}
.step-line {
  width: 2px;
  flex: 1;
  min-height: 20px;
  margin: 4px 0;
  transition: background 0.2s;
}
.step-line.step-pending { background: #e2e8f0; }
.step-line.step-in-progress { background: #93c5fd; }
.step-line.step-completed { background: #6ee7b7; }

.step-content {
  flex: 1;
  padding: 0 0 16px 0;
  min-width: 0;
}
.step-header {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.step-name {
  font-weight: 600;
  font-size: 13px;
  color: #1e293b;
}
.step-type-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
}
.type-click    { background: #dbeafe; color: #1d4ed8; }
.type-callback { background: #fef3c7; color: #b45309; }
.type-progress { background: #ede9fe; color: #6d28d9; }
.type-reward   { background: #d1fae5; color: #047857; }
.type-passive  { background: #fce7f3; color: #be185d; }

.step-status-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
}
.step-status-tag.step-pending { background: #f1f5f9; color: #64748b; }
.step-status-tag.step-in-progress { background: #dbeafe; color: #1d4ed8; }
.step-status-tag.step-completed { background: #d1fae5; color: #047857; }

.step-desc {
  font-size: 12px;
  color: #64748b;
  margin: 4px 0;
  line-height: 1.5;
}
.progress-bar-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0;
}
.progress-bar {
  flex: 1;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea, #764ba2);
  border-radius: 3px;
  transition: width 0.3s ease;
}
.progress-text {
  font-size: 11px;
  color: #6d28d9;
  font-weight: 600;
  white-space: nowrap;
}
.step-time {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.drawer-task-link {
  color: #6d28d9;
  font-weight: 600;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s;
}
.drawer-task-link:hover {
  color: #4c1d95;
  text-decoration: underline;
}

.detail-actions {
  display: flex;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}
</style>
