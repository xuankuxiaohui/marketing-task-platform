<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="detail-header">
        <el-button text @click="$router.push('/mutex-groups')" class="back-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
          返回列表
        </el-button>
        <div class="header-main">
          <span class="page-title">{{ group?.name }}</span>
          <span :class="['scope-pill', group?.scope === 'FULL_LIFECYCLE' ? 'scope-full' : 'scope-cycle']">
            {{ group?.scope === 'FULL_LIFECYCLE' ? '全生命周期' : '同周期' }}
          </span>
        </div>
        <p class="page-sub" v-if="group?.description">{{ group?.description }}</p>
      </div>
    </template>

    <p class="section-label">组内任务 ({{ tasks.length }})</p>
    <el-table :data="tasks" v-if="tasks.length">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="code" label="编码" min-width="120">
        <template #default="{ row }">
          <code class="code-cell">{{ row.code }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="120">
        <template #default="{ row }">
          <span class="name-cell">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <span :class="['status-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="periodType" label="周期" width="100">
        <template #default="{ row }">
          <span :class="['period-pill', periodClass(row.periodType)]">{{ periodLabel(row.periodType) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button size="small" type="primary" plain @click="$router.push(`/tasks/${scope.row.id}`)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="暂无任务加入此互斥组" />
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMutexGroup, getMutexGroupTasks, type MutexGroup } from '../../api/mutex-group'

const route = useRoute()
const groupId = Number(route.params.id)

const group = ref<MutexGroup | null>(null)
const tasks = ref<any[]>([])
const loading = ref(false)

const statusLabel = (s: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }[s] || s)
const statusClass = (s: string) => ({ DRAFT: 'draft', PUBLISHED: 'published', OFFLINE: 'offline' }[s] || '')

const periodLabel = (t: string) => ({ ONCE: '一次性', DAILY: '每日', MONTHLY: '每月', CRON: 'Cron', SPECIAL: '特殊' }[t] || t)
const periodClass = (t: string) => ({ ONCE: 'period-once', DAILY: 'period-daily', MONTHLY: 'period-monthly', CRON: 'period-cron', SPECIAL: 'period-special' }[t] || '')

async function load() {
  loading.value = true
  try {
    const [gRes, tRes] = await Promise.all([
      getMutexGroup(groupId),
      getMutexGroupTasks(groupId),
    ])
    group.value = gRes.data.data
    tasks.value = tRes.data.data
  } catch (e) {
    ElMessage.error('加载互斥组详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.back-btn {
  align-self: flex-start;
  color: #6d28d9;
  padding: 0;
  font-size: 12px;
  margin-bottom: 4px;
}
.header-main {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-title {
  font-size: 16px;
  font-weight: 700;
  color: #2d1b69;
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
}

.scope-pill {
  display: inline-block;
  padding: 1px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.6;
}
.scope-cycle { background: #dbeafe; color: #1d4ed8; }
.scope-full { background: #fce7f3; color: #be185d; }

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
</style>
