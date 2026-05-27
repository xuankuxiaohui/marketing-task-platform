<template>
  <el-card>
    <template #header>
      <div class="edit-header">
        <span>{{ isEdit ? '编辑任务' : '新建任务' }}</span>
        <span class="edit-sub">配置任务基本信息、步骤、过滤器和端入口</span>
      </div>
    </template>
    <el-tabs v-model="activeTab" class="edit-tabs">
      <el-tab-pane label="基本信息" name="basic">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            基本信息
          </span>
        </template>
        <BasicTab v-model="task" />
      </el-tab-pane>
      <el-tab-pane label="步骤配置" name="steps">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
            步骤配置
          </span>
        </template>
        <StepsTab ref="stepsTabRef" :task-id="taskId" />
      </el-tab-pane>
      <el-tab-pane label="过滤器" name="filters">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/></svg>
            过滤器
          </span>
        </template>
        <FiltersTab ref="filtersTabRef" />
      </el-tab-pane>
      <el-tab-pane label="端配置" name="platforms">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            端配置
          </span>
        </template>
        <PlatformsTab ref="platformsTabRef" />
      </el-tab-pane>
      <el-tab-pane label="模拟测试" name="simulate">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
            模拟测试
          </span>
        </template>
        <SimulateTab v-if="taskId" :task-id="taskId" />
      </el-tab-pane>
    </el-tabs>
    <div class="form-actions">
      <el-button type="primary" @click="submit" :loading="submitting">保存草稿</el-button>
      <el-button v-if="isEdit" @click="openVersions">版本历史</el-button>
      <el-button @click="$router.push('/tasks')">取消</el-button>
    </div>

    <!-- Version History Dialog -->
    <el-dialog v-model="versionsDialogVisible" title="版本历史" width="900px" destroy-on-close>
      <template v-if="versions.length">
        <div class="version-compare-layout">
          <div class="version-list">
            <div class="version-list-title">版本列表</div>
            <div
              v-for="v in versions"
              :key="v.id"
              :class="['version-item', {
                'selected-left': compareLeft?.id === v.id,
                'selected-right': compareRight?.id === v.id,
              }]"
              @click="selectVersion(v)"
            >
              <span class="version-num">v{{ v.version }}</span>
              <span class="version-time">{{ formatTime(v.createdAt) }}</span>
            </div>
          </div>
          <div class="version-diff">
            <div class="diff-header">
              <span class="diff-label">对比</span>
              <span v-if="compareLeft" class="diff-version-tag left">v{{ compareLeft.version }}</span>
              <span v-if="compareLeft && compareRight" class="diff-vs">vs</span>
              <span v-if="compareRight" class="diff-version-tag right">v{{ compareRight.version }}</span>
            </div>
            <div v-if="compareLeft && compareRight" class="diff-content">
              <el-table :data="diffRows" size="small" border>
                <el-table-column prop="field" label="字段" width="120" />
                <el-table-column prop="left" label="v{{ compareLeft.version }}">
                  <template #default="{ row }">
                    <span :class="{ 'diff-changed': row.changed }">{{ row.left || '--' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="right" label="v{{ compareRight.version }}">
                  <template #default="{ row }">
                    <span :class="{ 'diff-changed': row.changed }">{{ row.right || '--' }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <el-empty v-else description="选择两个版本进行对比" />
          </div>
        </div>
      </template>
      <el-empty v-else description="暂无历史版本" />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'TaskEdit' })
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { saveTaskAggregate, getTaskById, getTaskVersions, getTaskVersionDetail, type Task, type TaskVersion, type TaskVersionDetail } from '../../api/task'
import { listSteps, listTransitions } from '../../api/step'
import { listFilters } from '../../api/filter'
import { listPlatforms } from '../../api/platform'
import { listStepPlatforms } from '../../api/step-platform'
import BasicTab from './tabs/BasicTab.vue'
import FiltersTab from './tabs/FiltersTab.vue'
import PlatformsTab from './tabs/PlatformsTab.vue'
import StepsTab from './tabs/StepsTab.vue'
import SimulateTab from './SimulateTab.vue'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => taskId.value !== null)
const submitting = ref(false)
const activeTab = ref('basic')
const versionsDialogVisible = ref(false)
const versions = ref<TaskVersion[]>([])
const compareLeft = ref<TaskVersion | null>(null)
const compareRight = ref<TaskVersion | null>(null)
const leftSnapshot = ref<TaskVersionDetail | null>(null)
const rightSnapshot = ref<TaskVersionDetail | null>(null)
const diffRows = ref<any[]>([])

const task = ref<Task>({
  code: '',
  name: '',
  description: '',
  periodType: 'DAILY',
  status: 'DRAFT',
})

const stepsTabRef = ref()
const filtersTabRef = ref()
const platformsTabRef = ref()

onMounted(async () => {
  if (taskId.value) {
    try {
      const [{ data: taskResp }, { data: stepsResp }, { data: filtersResp }, { data: platformsResp }, { data: spResp }, { data: trResp }] = await Promise.all([
        getTaskById(taskId.value),
        listSteps(taskId.value),
        listFilters(taskId.value),
        listPlatforms(taskId.value),
        listStepPlatforms(taskId.value),
        listTransitions(taskId.value),
      ])
      task.value = taskResp.data
      const stepsData = stepsResp.data || []
      stepsTabRef.value?.setSteps(stepsData)
      stepsTabRef.value?.setTransitions(trResp.data || [])
      filtersTabRef.value?.setFilters(filtersResp.data)
      platformsTabRef.value?.setPlatforms(platformsResp.data)
      platformsTabRef.value?.setSteps(stepsData)
      platformsTabRef.value?.setStepPlatforms(spResp.data || [])
    } catch (e) {
      console.error('Failed to load task data:', e)
    }
  }
})

async function submit() {
  submitting.value = true
  try {
    const dto = {
      task: task.value,
      steps: stepsTabRef.value?.getSteps(),
      filters: filtersTabRef.value?.getFilters(),
      platforms: platformsTabRef.value?.getPlatforms(),
      stepPlatforms: platformsTabRef.value?.getStepPlatforms(),
      transitions: stepsTabRef.value?.getTransitions(),
    }
    await saveTaskAggregate(dto)
    ElMessage.success('保存成功')
    await router.push('/tasks')
  } catch (e: any) {
    const msg = e.response?.data?.message || '保存失败'
    ElMessage.error(msg)
    console.error('Failed to save task:', e)
  } finally {
    submitting.value = false
  }
}

async function openVersions() {
  if (!taskId.value) return
  versionsDialogVisible.value = true
  try {
    const { data } = await getTaskVersions(taskId.value)
    versions.value = data.data || []
    if (versions.value.length >= 2) {
      compareLeft.value = versions.value[0]
      compareRight.value = versions.value[1]
      await loadDiff()
    } else if (versions.value.length === 1) {
      compareLeft.value = versions.value[0]
      compareRight.value = null
    }
  } catch (e: any) {
    ElMessage.error('加载版本历史失败')
  }
}

async function selectVersion(v: TaskVersion) {
  if (!compareLeft.value) {
    compareLeft.value = v
  } else if (!compareRight.value) {
    compareRight.value = v
    await loadDiff()
  } else {
    // reset: select new left, clear right
    compareLeft.value = v
    compareRight.value = null
    leftSnapshot.value = null
    rightSnapshot.value = null
    diffRows.value = []
  }
}

async function loadDiff() {
  if (!compareLeft.value || !compareRight.value || !taskId.value) return
  try {
    const [leftRes, rightRes] = await Promise.all([
      getTaskVersionDetail(taskId.value, compareLeft.value.id),
      getTaskVersionDetail(taskId.value, compareRight.value.id),
    ])
    leftSnapshot.value = leftRes.data.data
    rightSnapshot.value = rightRes.data.data
    computeDiff()
  } catch (e) {
    ElMessage.error('加载版本详情失败')
  }
}

function safeParse(json: string) {
  try {
    return JSON.parse(json)
  } catch {
    return null
  }
}

function computeDiff() {
  const left = safeParse(leftSnapshot.value?.snapshotJson || '')
  const right = safeParse(rightSnapshot.value?.snapshotJson || '')
  if (!left || !right) {
    diffRows.value = []
    return
  }
  const lt = left.task || {}
  const rt = right.task || {}
  const rows: any[] = []

  // Basic info
  const fieldMap: Record<string, [string, string]> = {
    '名称': [lt.name, rt.name],
    '编码': [lt.code, rt.code],
    '描述': [lt.description, rt.description],
    '周期类型': [lt.periodType, rt.periodType],
    '状态': [lt.status, rt.status],
    '版本': [lt.version != null ? String(lt.version) : '', rt.version != null ? String(rt.version) : ''],
  }
  for (const [field, [l, r]] of Object.entries(fieldMap)) {
    rows.push({
      field,
      left: l || '--',
      right: r || '--',
      changed: l !== r,
    })
  }

  // Steps
  const ls = left.steps || []
  const rs = right.steps || []
  const maxSteps = Math.max(ls.length, rs.length)
  for (let i = 0; i < maxSteps; i++) {
    const lsName = ls[i]?.name || ''
    const rsName = rs[i]?.name || ''
    rows.push({
      field: `步骤 ${i + 1}`,
      left: ls[i] ? `${ls[i].code}: ${ls[i].name}` : '--',
      right: rs[i] ? `${rs[i].code}: ${rs[i].name}` : '--',
      changed: ls[i]?.code !== rs[i]?.code || lsName !== rsName,
    })
  }

  // Filters
  const lf = left.filters || []
  const rf = right.filters || []
  const maxF = Math.max(lf.length, rf.length)
  for (let i = 0; i < maxF; i++) {
    const lfExpr = lf[i]?.expression || ''
    const rfExpr = rf[i]?.expression || ''
    rows.push({
      field: `过滤器 ${i + 1}`,
      left: lfExpr || '--',
      right: rfExpr || '--',
      changed: lfExpr !== rfExpr,
    })
  }

  // Platforms
  const lp = left.platforms || []
  const rp = right.platforms || []
  const maxP = Math.max(lp.length, rp.length)
  for (let i = 0; i < maxP; i++) {
    const lCode = lp[i]?.platform || lp[i]?.code || ''
    const rCode = rp[i]?.platform || rp[i]?.code || ''
    rows.push({
      field: `端配置 ${i + 1}`,
      left: lCode || '--',
      right: rCode || '--',
      changed: lCode !== rCode,
    })
  }

  diffRows.value = rows
}

function formatTime(t: string | undefined) {
  if (!t) return '--'
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.edit-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.edit-header span:first-child {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.edit-sub {
  font-size: 12px;
  color: var(--color-text-muted);
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.edit-tabs {
  margin-top: 4px;
}

.form-actions {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border-light);
  display: flex;
  gap: 12px;
}

/* Version Compare */
.version-compare-layout {
  display: flex;
  gap: 16px;
  min-height: 400px;
}
.version-list {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid var(--color-border);
  padding-right: 12px;
  overflow-y: auto;
  max-height: 500px;
}
.version-list-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}
.version-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s;
}
.version-item:hover {
  background: var(--color-surface-hover);
}
.version-item.selected-left {
  background: var(--color-brand-primary-subtle);
  border-left: 3px solid var(--color-brand-primary);
}
.version-item.selected-right {
  background: var(--color-success-subtle);
  border-left: 3px solid var(--color-success);
}
.version-num {
  font-weight: 700;
  font-size: 13px;
  color: var(--color-text-primary);
}
.version-time {
  font-size: 11px;
  color: var(--color-text-muted);
}
.version-diff {
  flex: 1;
  min-width: 0;
}
.diff-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.diff-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.diff-version-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 700;
}
.diff-version-tag.left {
  background: var(--color-brand-primary-subtle);
  color: var(--color-brand-primary);
}
.diff-version-tag.right {
  background: var(--color-success-subtle);
  color: var(--color-success);
}
.diff-vs {
  font-size: 12px;
  color: var(--color-text-muted);
}
.diff-changed {
  background: var(--color-warning-light, #fef3c7);
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: 600;
}
</style>
