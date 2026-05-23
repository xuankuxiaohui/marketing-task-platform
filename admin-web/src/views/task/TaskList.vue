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
    <el-table :data="rows">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="code" label="编码" min-width="140">
        <template #default="{ row }">
          <code class="code-cell">{{ row.code }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="120">
        <template #default="{ row }">
          <span class="name-cell">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="periodType" label="周期" width="110">
        <template #default="{ row }">
          <span :class="['period-pill', periodClass(row.periodType)]">{{ periodLabel(row.periodType) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <span :class="['status-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" align="center">
        <template #default="{ row }">
          <span class="version-badge">v{{ row.version }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="scope">
          <el-button size="small" type="primary" plain @click="$router.push(`/tasks/${scope.row.id}`)">编辑</el-button>
          <el-button size="small" type="success" plain @click="publish(scope.row.id)">发布</el-button>
          <el-button size="small" type="danger" plain @click="offline(scope.row.id)">下线</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listTasks, offlineTask, publishTask } from '../../api/task'

const rows = ref([])

const periodLabel = (t: string) => ({ ONCE: '一次性', DAILY: '每日', MONTHLY: '每月', CRON: 'Cron', SPECIAL: '特殊' }[t] || t)
const periodClass = (t: string) => ({ ONCE: 'period-once', DAILY: 'period-daily', MONTHLY: 'period-monthly', CRON: 'period-cron', SPECIAL: 'period-special' }[t] || '')

const statusLabel = (s: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }[s] || s)
const statusClass = (s: string) => ({ DRAFT: 'draft', PUBLISHED: 'published', OFFLINE: 'offline' }[s] || '')

async function load() {
  try {
    const { data } = await listTasks()
    rows.value = data.data.records
  } catch (e) {
    console.error('Failed to load tasks:', e)
  }
}

async function publish(id: number) {
  try {
    await publishTask(id)
    await load()
  } catch (e) {
    console.error('Failed to publish task:', e)
  }
}

async function offline(id: number) {
  try {
    await offlineTask(id)
    await load()
  } catch (e) {
    console.error('Failed to offline task:', e)
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
  color: #2d1b69;
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #a78bfa;
}

.code-cell {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 11px;
  background: #f5f3ff;
  color: #6d28d9;
  padding: 2px 6px;
  border-radius: 4px;
}
.name-cell {
  font-weight: 600;
  color: #2d1b69;
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
.period-daily { background: #dbeafe; color: #1d4ed8; }
.period-once { background: #fef3c7; color: #b45309; }
.period-monthly { background: #ede9fe; color: #6d28d9; }
.period-cron { background: #fce7f3; color: #be185d; }
.period-special { background: #d1fae5; color: #047857; }

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
.status-pill.published { background: #dcfce7; color: #16a34a; }
.status-pill.published::before { background: #16a34a; }
.status-pill.draft { background: #fef3c7; color: #b45309; }
.status-pill.draft::before { background: #f59e0b; }
.status-pill.offline { background: #f1f5f9; color: #64748b; }
.status-pill.offline::before { background: #94a3b8; }

.version-badge {
  color: #a78bfa;
  font-weight: 600;
  font-size: 12px;
}
</style>
