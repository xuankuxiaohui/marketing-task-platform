<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">操作日志</span>
          <p class="page-sub">追踪任务、奖品、互斥组的配置变更记录</p>
        </div>
      </div>
    </template>

    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="操作类型">
        <el-select v-model="filters.operationType" placeholder="全部" clearable style="width:130px">
          <el-option v-for="t in operationTypes" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标类型">
        <el-select v-model="filters.targetType" placeholder="全部" clearable style="width:130px">
          <el-option v-for="t in targetTypes" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作人">
        <el-input v-model="filters.operatorId" placeholder="输入操作人ID" clearable style="width:150px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="时间范围">
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

    <el-table :data="rows" v-loading="loading" element-loading-text="加载中...">
      <el-table-column label="时间" width="170">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作人" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="operator-name">{{ row.operatorName || row.operatorId }}</span>
          <span v-if="row.operatorName && row.operatorName !== row.operatorId" class="operator-id">#{{ row.operatorId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作类型" width="100">
        <template #default="{ row }">
          <span :class="['op-type-tag', opTypeClass(row.operationType)]">{{ opTypeLabel(row.operationType) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="目标类型" width="110">
        <template #default="{ row }">
          <span class="target-type-tag">{{ targetTypeLabel(row.targetType) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="目标名称" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="target-name">{{ row.targetName || `#${row.targetId}` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变更详情" min-width="140">
        <template #default="{ row }">
          <el-popover v-if="row.detail" placement="left" :width="360" trigger="click">
            <template #reference>
              <el-button size="small" text type="primary">查看详情</el-button>
            </template>
            <pre class="detail-json">{{ formatJson(row.detail) }}</pre>
          </el-popover>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
    </el-table>

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
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'OperationLogs' })
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listOperationLogs, type OperationLogVO, type OperationLogQueryParams } from '../api/operation-log'

const rows = ref<OperationLogVO[]>([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({ page: 1, size: 20 })
const filters = reactive<OperationLogQueryParams>({})
const dateRange = ref<[string, string] | null>(null)

const operationTypes = [
  { value: 'CREATE', label: '创建' },
  { value: 'UPDATE', label: '更新' },
  { value: 'PUBLISH', label: '发布' },
  { value: 'OFFLINE', label: '下线' },
  { value: 'DELETE', label: '删除' },
]

const targetTypes = [
  { value: 'TASK', label: '任务' },
  { value: 'PRIZE', label: '奖品' },
  { value: 'MUTEX_GROUP', label: '互斥组' },
]

const opTypeLabel = (t: string) => ({
  CREATE: '创建', UPDATE: '更新', PUBLISH: '发布',
  OFFLINE: '下线', DELETE: '删除',
}[t] || t)

const opTypeClass = (t: string) => ({
  CREATE: 'type-create', UPDATE: 'type-update', PUBLISH: 'type-publish',
  OFFLINE: 'type-offline', DELETE: 'type-delete',
}[t] || '')

const targetTypeLabel = (t: string) => ({
  TASK: '任务', PRIZE: '奖品', MUTEX_GROUP: '互斥组',
}[t] || t)

const formatTime = (t: string | null | undefined) => {
  if (!t) return '--'
  return t.replace('T', ' ').substring(0, 19)
}

const formatJson = (s: string) => {
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

async function load() {
  loading.value = true
  try {
    const params: OperationLogQueryParams = {
      page: pagination.page,
      size: pagination.size,
      ...filters,
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    Object.keys(params).forEach(k => {
      const key = k as keyof OperationLogQueryParams
      if (params[key] === undefined || params[key] === '' || params[key] === null) {
        delete params[key]
      }
    })

    const { data } = await listOperationLogs(params)
    rows.value = data.data.records || []
    total.value = data.data.total || 0
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载操作日志失败')
  } finally {
    loading.value = false
  }
}

function search() { pagination.page = 1; load() }
function reset() {
  filters.operationType = undefined
  filters.targetType = undefined
  filters.operatorId = undefined
  dateRange.value = null
  filters.startDate = undefined
  filters.endDate = undefined
  pagination.page = 1
  load()
}

function onPageChange() { load() }
function onSizeChange() { pagination.page = 1; load() }

onMounted(load)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: flex-start; }
.page-title { font-size: 16px; font-weight: 700; color: var(--color-text-primary); }
.page-sub { margin: 2px 0 0; font-size: 12px; color: var(--color-text-muted); }
.filter-bar { margin-bottom: 16px; padding: 16px; background: var(--color-surface-raised); border-radius: 8px; border: 1px solid var(--color-border); }
.filter-bar :deep(.el-form-item) { margin-bottom: 8px; }
.time-cell { font-size: 12px; color: var(--color-text-secondary); }
.operator-name { font-weight: 600; font-size: 13px; color: var(--color-text-primary); }
.operator-id { font-size: 11px; color: var(--color-text-muted); margin-left: 4px; }
.target-name { font-size: 13px; color: var(--color-text-primary); font-weight: 500; }
.target-type-tag { display: inline-flex; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; background: var(--el-color-primary-light-9); color: var(--color-brand-primary); }
.empty-cell { color: var(--color-text-disabled); font-size: 12px; }
.op-type-tag { display: inline-flex; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.type-create  { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.type-update  { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.type-publish { background: var(--color-brand-primary-subtle); color: var(--color-brand-primary); }
.type-offline { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.type-delete  { background: #fef2f2; color: #dc2626; }
.detail-json { margin: 0; font-family: var(--font-mono); font-size: 11px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; color: var(--color-text-secondary); background: var(--color-surface-raised); padding: 12px; border-radius: 6px; border: 1px solid var(--color-border); max-height: 300px; overflow-y: auto; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
