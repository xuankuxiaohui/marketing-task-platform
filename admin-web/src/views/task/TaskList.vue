<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">任务管理</span>
          <p class="page-sub">管理和配置营销任务</p>
        </div>
        <el-button type="primary" @click="$router.push('/tasks/new')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建任务
        </el-button>
      </div>
    </template>

    <!-- search / filter bar -->
    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索任务名称或编码"
        clearable
        style="width: 260px"
        @clear="search"
        @keyup.enter="search"
      >
        <template #prefix>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" style="color: var(--color-text-muted)"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </template>
      </el-input>
      <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
        <el-option label="全部状态" value="" />
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="已下线" value="OFFLINE" />
        <el-option label="定时发布" value="SCHEDULED" />
        <el-option label="已删除" value="DELETED" />
      </el-select>
      <el-select v-model="filters.periodType" placeholder="全部周期" clearable style="width: 140px" @change="search">
        <el-option label="全部周期" value="" />
        <el-option label="一次性" value="ONCE" />
        <el-option label="每日" value="DAILY" />
        <el-option label="每月" value="MONTHLY" />
        <el-option label="Cron" value="CRON" />
        <el-option label="特殊" value="SPECIAL" />
      </el-select>
      <el-button type="primary" @click="search" style="margin-left:8px">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <!-- batch action bar -->
    <div v-if="selectedRows.length > 0 && filters.status !== 'DELETED'" class="batch-bar">
      <span class="batch-info">已选择 {{ selectedRows.length }} 个任务</span>
      <el-button type="primary" size="small" :loading="batchPublishing" @click="batchPublish">批量发布</el-button>
      <el-button type="danger" size="small" :loading="batchOfflining" @click="batchOffline">批量下线</el-button>
      <el-button size="small" @click="clearSelection">取消选择</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" @selection-change="onSelectionChange">
      <el-table-column v-if="filters.status !== 'DELETED'" type="selection" width="50" />
      <el-table-column prop="id" label="ID" width="75" align="center" />
      <el-table-column prop="code" label="编码" min-width="140">
        <template #default="{ row }">
          <code class="code-cell">{{ row.code }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="activityCode" label="活动编码" width="140">
        <template #default="{ row }">
          <span class="code-cell">{{ row.activityCode ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140">
        <template #default="{ row }">
          <span class="name-cell">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="描述" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip
            v-if="row.description"
            :content="row.description"
            placement="top"
            :show-after="400"
            effect="light"
          >
            <span class="desc-cell">{{ truncate(row.description, 30) }}</span>
          </el-tooltip>
          <span v-else class="desc-empty">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="periodType" label="周期" width="100">
        <template #default="{ row }">
          <span :class="['period-pill', periodClass(row.periodType)]">{{ periodLabel(row.periodType) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="步骤" width="70" align="center">
        <template #default="{ row }">
          <span class="count-badge">{{ row.stepCount ?? '--' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="实例" width="70" align="center">
        <template #default="{ row }">
          <span class="count-badge count-instance">{{ row.instanceCount ?? '--' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="105">
        <template #default="{ row }">
          <el-tooltip :content="statusTooltip(row.status)" placement="top" :show-after="300" effect="light">
            <span :class="['status-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="互斥组" width="130">
        <template #default="{ row }">
          <template v-if="row.mutexGroupId && getMutexGroupName(row.mutexGroupId)">
            <el-link type="primary" @click="$router.push(`/mutex-groups/${row.mutexGroupId}`)" style="font-size:12px">
              {{ getMutexGroupName(row.mutexGroupId) }}
            </el-link>
          </template>
          <span v-else style="color:var(--color-text-muted);font-size:11px">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="65" align="center">
        <template #default="{ row }">
          <span class="version-badge">v{{ row.version }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="160">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.updatedAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <div class="action-cell">
            <template v-if="row.status !== 'DELETED'">
              <el-button size="small" type="primary" @click="$router.push(`/tasks/${row.id}`)">编辑</el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, row)">
                <el-button size="small" text>
                  更多
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="margin-left:2px;vertical-align:-1px"><polyline points="6 9 12 15 18 9"/></svg>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="copy">复制</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'PUBLISHED'" command="simulate">模拟测试</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'DRAFT'" command="publish">发布</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'" command="schedule">定时发布</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'SCHEDULED'" command="cancelSchedule">取消定时</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'PUBLISHED'" command="offline">下线</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'OFFLINE'" command="publish">上线</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <span style="color:var(--el-color-danger)">删除</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <template v-else>
              <el-button size="small" type="success" plain @click="handleRestore(row)">恢复</el-button>
              <span class="deleted-label">已删除</span>
            </template>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- pagination -->
    <div class="pagination-wrap" v-if="total > 0">
      <el-pagination
        v-model:current-page="pagination.page"
        :page-size="pagination.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>

    <!-- Copy dialog -->
    <el-dialog v-model="copyDialogVisible" title="复制任务" width="400px">
      <el-form label-width="80px">
        <el-form-item label="原任务">
          <el-input :model-value="copyForm.originalName" disabled />
        </el-form-item>
        <el-form-item label="新名称">
          <el-input v-model="copyForm.name" placeholder="请输入新任务名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="copyingId === copyForm.id" @click="confirmCopy">确认复制</el-button>
      </template>
    </el-dialog>

    <!-- Schedule publish dialog -->
    <el-dialog v-model="scheduleDialogVisible" title="定时发布" width="400px">
      <el-form label-width="80px">
        <el-form-item label="任务">
          <el-input :model-value="scheduleForm.taskName" disabled />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="scheduleForm.publishAt"
            type="datetime"
            placeholder="选择发布时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="schedulingId === scheduleForm.taskId" @click="confirmSchedule">确认定时</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'TaskList' })
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listTasks, offlineTask, publishTask, copyTask, deleteTask, restoreTask, batchPublishTasks, batchOfflineTasks, schedulePublishTask, cancelSchedulePublish } from '../../api/task'
import { listMutexGroups, type MutexGroup } from '../../api/mutex-group'

const router = useRouter()
const rows = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const mutexGroupMap = ref<Record<number, string>>({})
const publishingId = ref<number | null>(null)
const offliningId = ref<number | null>(null)
const copyingId = ref<number | null>(null)
const deletingId = ref<number | null>(null)

// Copy dialog state
const copyDialogVisible = ref(false)
const copyForm = reactive({ id: 0, name: '', originalName: '' })

// Batch operation state
const selectedRows = ref<any[]>([])
const batchPublishing = ref(false)
const batchOfflining = ref(false)

// Schedule dialog state
const scheduleDialogVisible = ref(false)
const scheduleForm = reactive({ taskId: 0, taskName: '', publishAt: '' })
const schedulingId = ref<number | null>(null)
const cancellingId = ref<number | null>(null)

const pagination = reactive({ page: 1, size: 20 })
const filters = reactive({ keyword: '', status: '', periodType: '' })

function getMutexGroupName(id: number) {
  return mutexGroupMap.value[id]
}

const periodLabel = (t: string) => ({ ONCE: '一次性', DAILY: '每日', MONTHLY: '每月', CRON: 'Cron', SPECIAL: '特殊' }[t] || t)
const periodClass = (t: string) => ({ ONCE: 'period-once', DAILY: 'period-daily', MONTHLY: 'period-monthly', CRON: 'period-cron', SPECIAL: 'period-special' }[t] || '')

const statusLabel = (s: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线', SCHEDULED: '定时发布', DELETED: '已删除' }[s] || s)
const statusClass = (s: string) => ({ DRAFT: 'draft', PUBLISHED: 'published', OFFLINE: 'offline', SCHEDULED: 'scheduled', DELETED: 'deleted' }[s] || '')

function statusTooltip(s: string) {
  return {
    DRAFT: '草稿状态：任务尚未发布，C端用户不可见',
    PUBLISHED: '已发布状态：任务在线，C 端用户可见并可参与',
    OFFLINE: '已下线状态：任务已停止，C 端用户不可见',
    SCHEDULED: '定时发布：任务将在指定时间自动发布',
    DELETED: '已删除：任务已被逻辑删除',
  }[s] || s
}

function truncate(text: string, max: number) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '...' : text
}

function formatTime(t: string | undefined) {
  if (!t) return '--'
  // ISO format like "2026-05-24T10:30:00" -> "2026-05-24 10:30"
  return t.replace('T', ' ').substring(0, 16)
}

async function load() {
  loading.value = true
  try {
    const params: any = { page: pagination.page, size: pagination.size }
    if (filters.status) params.status = filters.status
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.periodType) params.periodType = filters.periodType

    const [{ data: taskData }, { data: groupData }] = await Promise.all([
      listTasks(params),
      listMutexGroups(),
    ])
    rows.value = taskData.data.records
    total.value = taskData.data.total
    const map: Record<number, string> = {}
    for (const g of groupData.data) {
      map[g.id] = g.name
    }
    mutexGroupMap.value = map
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载任务列表失败')
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  load()
}

function reset() {
  filters.keyword = ''
  filters.status = ''
  filters.periodType = ''
  pagination.page = 1
  load()
}

function onSizeChange() {
  pagination.page = 1
  load()
}

function onPageChange() {
  load()
}

async function publish(id: number) {
  publishingId.value = id
  try {
    await publishTask(id)
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '发布任务失败')
  } finally {
    publishingId.value = null
  }
}

async function offline(id: number) {
  offliningId.value = id
  try {
    await offlineTask(id)
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '下线任务失败')
  } finally {
    offliningId.value = null
  }
}

function openCopyDialog(row: any) {
  copyForm.id = row.id
  copyForm.originalName = row.name
  copyForm.name = row.name + ' (副本)'
  copyDialogVisible.value = true
}

async function confirmCopy() {
  copyingId.value = copyForm.id
  try {
    const { data } = await copyTask(copyForm.id, { name: copyForm.name })
    const newTaskId = data.data
    ElMessage.success('复制成功')
    copyDialogVisible.value = false
    router.push(`/tasks/${newTaskId}`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '复制任务失败')
  } finally {
    copyingId.value = null
  }
}

function onSelectionChange(selection: any[]) {
  selectedRows.value = selection
}

function clearSelection() {
  selectedRows.value = []
}

async function batchPublish() {
  const ids = selectedRows.value.map(r => r.id)
  if (ids.length === 0) return
  batchPublishing.value = true
  try {
    const { data } = await batchPublishTasks(ids)
    const result = data.data
    if (result.failed.length === 0) {
      ElMessage.success(`成功发布 ${result.success.length} 个任务`)
    } else {
      ElMessage.warning(`成功 ${result.success.length} 个，失败 ${result.failed.length} 个`)
    }
    clearSelection()
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '批量发布失败')
  } finally {
    batchPublishing.value = false
  }
}

async function batchOffline() {
  const ids = selectedRows.value.map(r => r.id)
  if (ids.length === 0) return
  batchOfflining.value = true
  try {
    const { data } = await batchOfflineTasks(ids)
    const result = data.data
    if (result.failed.length === 0) {
      ElMessage.success(`成功下线 ${result.success.length} 个任务`)
    } else {
      ElMessage.warning(`成功 ${result.success.length} 个，失败 ${result.failed.length} 个`)
    }
    clearSelection()
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '批量下线失败')
  } finally {
    batchOfflining.value = false
  }
}

function openScheduleDialog(row: any) {
  scheduleForm.taskId = row.id
  scheduleForm.taskName = row.name
  scheduleForm.publishAt = ''
  scheduleDialogVisible.value = true
}

async function confirmSchedule() {
  if (!scheduleForm.publishAt) {
    ElMessage.warning('请选择发布时间')
    return
  }
  schedulingId.value = scheduleForm.taskId
  try {
    await schedulePublishTask(scheduleForm.taskId, scheduleForm.publishAt)
    ElMessage.success('定时发布设置成功')
    scheduleDialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '设置定时发布失败')
  } finally {
    schedulingId.value = null
  }
}

async function cancelSchedule(id: number) {
  cancellingId.value = id
  try {
    await cancelSchedulePublish(id)
    ElMessage.success('已取消定时发布')
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '取消定时发布失败')
  } finally {
    cancellingId.value = null
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除任务「${row.name}」？删除后不可恢复。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    deletingId.value = row.id
    await deleteTask(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '删除任务失败')
    }
  } finally {
    deletingId.value = null
  }
}

async function handleRestore(row: any) {
  try {
    await ElMessageBox.confirm(`确定恢复任务「${row.name}」？`, '确认恢复', {
      confirmButtonText: '恢复',
      cancelButtonText: '取消',
      type: 'info',
    })
    await restoreTask(row.id)
    ElMessage.success('已恢复')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '恢复任务失败')
    }
  }
}

function handleAction(command: string, row: any) {
  switch (command) {
    case 'copy':
      openCopyDialog(row)
      break
    case 'simulate':
      router.push(`/simulate?taskId=${row.id}`)
      break
    case 'publish':
      publish(row.id)
      break
    case 'schedule':
      openScheduleDialog(row)
      break
    case 'cancelSchedule':
      cancelSchedule(row.id)
      break
    case 'offline':
      offline(row.id)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.page-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

/* filter bar */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

/* cells */
.code-cell {
  font-family: var(--font-mono);
  font-size: 11px;
  background: var(--color-brand-primary-subtle);
  color: var(--color-brand-primary);
  padding: 2px 6px;
  border-radius: 4px;
}
.name-cell {
  font-weight: 600;
  color: var(--color-text-primary);
}
.desc-cell {
  color: var(--color-text-muted);
  font-size: 12px;
  cursor: default;
}
.desc-empty {
  color: var(--color-text-disabled);
  font-size: 11px;
}
.time-cell {
  color: var(--color-text-muted);
  font-size: 12px;
  white-space: nowrap;
}
.count-badge {
  display: inline-block;
  min-width: 24px;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-brand-primary);
  background: var(--color-brand-primary-subtle);
  text-align: center;
}
.count-badge.count-instance {
  color: var(--color-emerald-text);
  background: var(--color-emerald-subtle);
}

/* Period pills */
.period-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.6;
}
.period-daily { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.period-once { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.period-monthly { background: var(--color-brand-primary-subtle); color: var(--color-brand-primary); }
.period-cron { background: var(--color-pink-subtle); color: var(--color-pink-text); }
.period-special { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }

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
  cursor: default;
}
.status-pill::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.status-pill.published { background: var(--color-published-subtle); color: var(--color-published-text); }
.status-pill.published::before { background: var(--color-published-text); }
.status-pill.draft { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.status-pill.draft::before { background: var(--color-warning); }
.status-pill.offline { background: var(--color-border-light); color: var(--color-text-muted); }
.status-pill.offline::before { background: var(--color-text-disabled); }
.status-pill.scheduled { background: var(--color-pink-subtle); color: var(--color-pink-text); }
.status-pill.scheduled::before { background: var(--color-pink-text); }
.status-pill.deleted { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.status-pill.deleted::before { background: var(--el-color-danger); }

.version-badge {
  color: var(--color-text-muted);
  font-weight: 600;
  font-size: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* action cell */
.action-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}
.deleted-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

/* batch action bar */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
}
.batch-info {
  font-size: 13px;
  color: var(--color-text-primary);
  font-weight: 500;
}
</style>
