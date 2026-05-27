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
          <span v-if="group?.crossCycle" class="cross-cycle-badge">跨周期</span>
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
      <el-table-column label="操作" width="160">
        <template #default="scope">
          <el-button size="small" type="primary" plain @click="$router.push(`/tasks/${scope.row.id}`)">查看</el-button>
          <el-button size="small" type="danger" plain @click="handleUnlink(scope.row.id, scope.row.name)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="暂无任务加入此互斥组" />
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'MutexGroupDetail' })
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMutexGroup, getMutexGroupTasks, unlinkMutexGroupTask, type MutexGroup } from '../../api/mutex-group'

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

async function handleUnlink(taskId: number, taskName: string) {
  try {
    await ElMessageBox.confirm(
      `确定要将任务「${taskName}」从互斥组中移除吗？移除后该任务不再受互斥约束。`,
      '确认移除',
      { confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await unlinkMutexGroupTask(groupId, taskId)
    ElMessage.success('已移除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '移除失败')
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
  color: var(--color-brand-primary);
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
  color: var(--color-text-primary);
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
.section-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
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
.scope-cycle { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.scope-full { background: var(--color-pink-subtle); color: var(--color-pink-text); }

.cross-cycle-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 700;
  background: var(--color-emerald-subtle);
  color: var(--color-emerald-text);
}

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
.status-pill.published { background: var(--color-published-subtle); color: var(--color-published-text); }
.status-pill.published::before { background: var(--color-published-text); }
.status-pill.draft { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.status-pill.draft::before { background: var(--color-warning); }
.status-pill.offline { background: var(--color-border-light); color: var(--color-text-muted); }
.status-pill.offline::before { background: var(--color-text-disabled); }

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
</style>
