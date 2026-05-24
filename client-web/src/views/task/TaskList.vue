<template>
  <div class="task-list-page">
    <van-nav-bar title="任务中心" @click-right="handleSwitchUser">
      <template #right>
        <van-icon name="gift-o" size="20" class="nav-prize-icon" @click.stop="$router.push('/prizes')" />
        <span class="nav-logout" @click="handleSwitchUser">退出</span>
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" @refresh="loadTasks" class="pull-area">
      <van-loading v-if="loading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

      <van-empty v-else-if="tasks.length === 0" description="暂无可用任务" />

      <div v-else class="task-cards">
        <div
          v-for="task in tasks"
          :key="task.id"
          :class="['task-card', periodBorder(task.periodType)]"
          @click="$router.push(`/task/${task.id}`)"
        >
          <div class="card-body">
            <div class="card-left">
              <div :class="['period-icon', periodIconBg(task.periodType)]">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
                  <rect v-if="task.periodType === 'ONCE'" x="3" y="3" width="18" height="18" rx="3"/>
                  <g v-else-if="task.periodType === 'DAILY'">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                  </g>
                  <g v-else>
                    <path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>
                  </g>
                </svg>
              </div>
            </div>
            <div class="card-center">
              <div class="card-title">{{ task.name }}</div>
              <div class="card-desc">{{ task.description || '暂无描述' }}</div>
            </div>
            <div class="card-right">
              <span :class="['period-tag', periodTagBg(task.periodType)]">{{ periodLabel(task.periodType) }}</span>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#cbd5e1" stroke-width="2" stroke-linecap="round"><polyline points="9 18 15 12 9 6"/></svg>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listTasks, type Task } from '../../api/task'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const tasks = ref<Task[]>([])
const loading = ref(false)
const refreshing = ref(false)

const periodLabel = (t: string) => {
  const map: Record<string, string> = { ONCE: '一次性', DAILY: '每日', MONTHLY: '每月', CRON: '定时', SPECIAL: '特殊' }
  return map[t] || t
}

const periodBorder = (t: string) => {
  const map: Record<string, string> = { ONCE: 'border-once', DAILY: 'border-daily', MONTHLY: 'border-monthly', CRON: 'border-cron', SPECIAL: 'border-special' }
  return map[t] || 'border-once'
}

const periodIconBg = (t: string) => {
  const map: Record<string, string> = { ONCE: 'icon-once', DAILY: 'icon-daily', MONTHLY: 'icon-monthly', CRON: 'icon-cron', SPECIAL: 'icon-special' }
  return map[t] || 'icon-once'
}

const periodTagBg = (t: string) => {
  const map: Record<string, string> = { ONCE: 'tag-once', DAILY: 'tag-daily', MONTHLY: 'tag-monthly', CRON: 'tag-cron', SPECIAL: 'tag-special' }
  return map[t] || 'tag-once'
}

function handleSwitchUser() {
  userStore.logout()
  router.push('/login')
}

async function loadTasks() {
  try {
    loading.value = true
    const { data } = await listTasks()
    tasks.value = data.data || []
  } catch (e: any) {
    showToast(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(loadTasks)
</script>

<style scoped>
.nav-prize-icon {
  margin-right: 14px;
  color: #f59e0b;
  cursor: pointer;
}
.nav-logout {
  font-size: 13px;
  color: #94a3b8;
  cursor: pointer;
}

.task-list-page {
  min-height: 100vh;
  background: #f7f8fa;
}

.pull-area {
  min-height: calc(100vh - 46px);
}

.loading-wrap {
  margin-top: 60px;
}

.task-cards {
  padding: 12px 14px;
}

.task-card {
  background: #fff;
  border-radius: 10px;
  margin-bottom: 10px;
  border-left: 4px solid #2563eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s;
}
.task-card:active {
  transform: scale(0.985);
}

.border-once { border-left-color: #f59e0b; }
.border-daily { border-left-color: #3b82f6; }
.border-monthly { border-left-color: #8b5cf6; }
.border-cron { border-left-color: #ec4899; }
.border-special { border-left-color: #10b981; }

.card-body {
  display: flex;
  align-items: center;
  padding: 14px 14px 14px 12px;
  gap: 12px;
}

.card-left {
  flex-shrink: 0;
}
.period-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.icon-once { background: #fef3c7; color: #d97706; }
.icon-daily { background: #dbeafe; color: #2563eb; }
.icon-monthly { background: #ede9fe; color: #7c3aed; }
.icon-cron { background: #fce7f3; color: #db2777; }
.icon-special { background: #d1fae5; color: #059669; }

.card-center {
  flex: 1;
  min-width: 0;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.card-desc {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.period-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}
.tag-once { background: #fef3c7; color: #92400e; }
.tag-daily { background: #dbeafe; color: #1d4ed8; }
.tag-monthly { background: #ede9fe; color: #6d28d9; }
.tag-cron { background: #fce7f3; color: #be185d; }
.tag-special { background: #d1fae5; color: #047857; }
</style>
