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

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="75" align="center" />
      <el-table-column prop="code" label="编码" min-width="140">
        <template #default="{ row }">
          <code class="code-cell">{{ row.code }}</code>
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
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="$router.push(`/tasks/${row.id}`)">编辑</el-button>
          <el-button
            size="small"
            type="default"
            :loading="copyingId === row.id"
            @click="copy(row.id)"
          >
            复制
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            size="small"
            type="success"
            :loading="publishingId === row.id"
            @click="publish(row.id)"
          >
            发布
          </el-button>
          <el-button
            v-if="row.status === 'PUBLISHED'"
            size="small"
            type="danger"
            :loading="offliningId === row.id"
            @click="offline(row.id)"
          >
            下线
          </el-button>
          <el-button
            v-if="row.status === 'OFFLINE'"
            size="small"
            type="success"
            :loading="publishingId === row.id"
            @click="publish(row.id)"
          >
            上线
          </el-button>
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
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'TaskList' })
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listTasks, offlineTask, publishTask, copyTask } from '../../api/task'
import { listMutexGroups, type MutexGroup } from '../../api/mutex-group'

const router = useRouter()
const rows = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const mutexGroupMap = ref<Record<number, string>>({})
const publishingId = ref<number | null>(null)
const offliningId = ref<number | null>(null)
const copyingId = ref<number | null>(null)

const pagination = reactive({ page: 1, size: 20 })
const filters = reactive({ keyword: '', status: '', periodType: '' })

function getMutexGroupName(id: number) {
  return mutexGroupMap.value[id]
}

const periodLabel = (t: string) => ({ ONCE: '一次性', DAILY: '每日', MONTHLY: '每月', CRON: 'Cron', SPECIAL: '特殊' }[t] || t)
const periodClass = (t: string) => ({ ONCE: 'period-once', DAILY: 'period-daily', MONTHLY: 'period-monthly', CRON: 'period-cron', SPECIAL: 'period-special' }[t] || '')

const statusLabel = (s: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }[s] || s)
const statusClass = (s: string) => ({ DRAFT: 'draft', PUBLISHED: 'published', OFFLINE: 'offline' }[s] || '')

function statusTooltip(s: string) {
  return {
    DRAFT: '草稿状态：任务尚未发布，C端用户不可见',
    PUBLISHED: '已发布状态：任务在线，C 端用户可见并可参与',
    OFFLINE: '已下线状态：任务已停止，C 端用户不可见',
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

async function copy(id: number) {
  copyingId.value = id
  try {
    const { data } = await copyTask(id)
    const newTaskId = data.data
    ElMessage.success('复制成功')
    router.push(`/tasks/${newTaskId}`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '复制任务失败')
  } finally {
    copyingId.value = null
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
</style>
