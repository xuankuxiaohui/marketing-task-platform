<template>
  <div class="task-list-page">
    <!-- Hero Header -->
    <div class="hero-header">
      <div class="hero-bg">
        <div class="hero-blob hero-blob-1"></div>
        <div class="hero-blob hero-blob-2"></div>
      </div>
      <div class="hero-content">
        <div class="hero-top">
          <div class="hero-greeting">
            <h1 class="hero-title">任务中心</h1>
            <p class="hero-subtitle">发现并完成任务，获取丰厚奖励</p>
          </div>
          <div class="hero-actions">
            <div class="hero-btn-circle" @click.stop="$router.push('/prizes')">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M20 12v10H4V12" />
                <path d="M2 7h20v5H2z" />
                <path d="M12 22V7" />
                <path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z" />
                <path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z" />
              </svg>
            </div>
            <div class="hero-btn-circle" @click="handleSwitchUser">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <polyline points="16 17 21 12 16 7" />
                <line x1="21" y1="12" x2="9" y2="12" />
              </svg>
            </div>
          </div>
        </div>

        <!-- Stats bar -->
        <div class="hero-stats" v-if="tasks.length > 0">
          <div class="stat-pill">
            <span class="stat-pill-value">{{ tasks.length }}</span>
            <span class="stat-pill-label">个任务</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-pill">
            <span class="stat-pill-value">{{ dailyCount }}</span>
            <span class="stat-pill-label">每日任务</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-pill">
            <span class="stat-pill-value">{{ specialCount }}</span>
            <span class="stat-pill-label">特殊任务</span>
          </div>
        </div>
      </div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="loadTasks" class="pull-area">
      <van-loading v-if="loading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

      <van-empty v-else-if="tasks.length === 0" description="暂无可用任务" image="search" />

      <div v-else class="task-cards">
        <div
          v-for="(task, idx) in tasks"
          :key="task.id"
          :class="['task-card', 'animate-in', `animate-in-delay-${Math.min(idx + 1, 8)}`]"
          @click="$router.push(`/task/${task.id}`)"
        >
          <div :class="['card-accent', accentClass(task.periodType)]"></div>

          <div class="card-content">
            <div class="card-top">
              <div :class="['period-icon', periodIconBg(task.periodType)]">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <template v-if="task.periodType === 'ONCE'">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                    <polyline points="22 4 12 14.01 9 11.01" />
                  </template>
                  <template v-else-if="task.periodType === 'DAILY'">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </template>
                  <template v-else-if="task.periodType === 'MONTHLY'">
                    <rect x="3" y="4" width="18" height="18" rx="2" />
                    <path d="M16 2v4M8 2v4M3 10h18" />
                  </template>
                  <template v-else-if="task.periodType === 'CRON'">
                    <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
                  </template>
                  <template v-else>
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                  </template>
                </svg>
              </div>
              <span :class="['period-tag', periodTagBg(task.periodType)]">{{ periodLabel(task.periodType) }}</span>
            </div>

            <div class="card-body">
              <h3 class="card-title">{{ task.name }}</h3>
              <p class="card-desc">{{ task.description || '暂无描述' }}</p>
            </div>

            <div class="card-footer">
              <div :class="['card-footer-line', `line-${task.periodType}`]"></div>
              <div class="card-action">
                <span class="action-text">查看详情</span>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="9 18 15 12 9 6"/></svg>
              </div>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from '../../utils/toast'
import { listTasks, type Task } from '../../api/task'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const tasks = ref<Task[]>([])
const loading = ref(false)
const refreshing = ref(false)

const dailyCount = computed(() => tasks.value.filter(t => t.periodType === 'DAILY').length)
const specialCount = computed(() => tasks.value.filter(t => t.periodType === 'SPECIAL' || t.periodType === 'CRON').length)

const periodLabel = (t: string) => {
  const map: Record<string, string> = { ONCE: '一次性', DAILY: '每日', MONTHLY: '每月', CRON: '定时', SPECIAL: '特殊' }
  return map[t] || t
}

const accentClass = (t: string) => {
  const map: Record<string, string> = { ONCE: 'accent-once', DAILY: 'accent-daily', MONTHLY: 'accent-monthly', CRON: 'accent-cron', SPECIAL: 'accent-special' }
  return map[t] || 'accent-once'
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
    showToast.fail(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(loadTasks)
</script>

<style scoped>
.task-list-page {
  min-height: 100vh;
  background: var(--color-bg);
}

/* ── Hero Header ── */
.hero-header {
  position: relative;
  overflow: hidden;
  padding: 0 0 20px;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #4f46e5 0%, #6366f1 40%, #818cf8 100%);
  z-index: 0;
}

.hero-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
}

.hero-blob-1 {
  width: 200px;
  height: 200px;
  background: rgba(167, 139, 250, 0.4);
  top: -60px;
  right: -40px;
}

.hero-blob-2 {
  width: 150px;
  height: 150px;
  background: rgba(236, 72, 153, 0.25);
  bottom: -30px;
  left: -30px;
}

.hero-content {
  position: relative;
  z-index: 1;
  padding: 48px 20px 0;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.hero-title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.3px;
}

.hero-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}

.hero-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.hero-btn-circle {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.hero-btn-circle:active {
  transform: scale(0.92);
  background: rgba(255, 255, 255, 0.25);
}

/* Stats bar */
.hero-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(12px);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.stat-pill {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-pill-value {
  font-size: 18px;
  font-weight: 800;
  color: #fff;
}

.stat-pill-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.stat-divider {
  width: 1px;
  height: 20px;
  background: rgba(255, 255, 255, 0.2);
}

/* Pull area */
.pull-area {
  min-height: calc(100vh - 200px);
}

.loading-wrap {
  margin-top: 60px;
}

/* ── Task Cards ── */
.task-cards {
  padding: 16px 16px 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-card {
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.task-card:active {
  transform: scale(0.98);
  box-shadow: var(--shadow-card-hover);
}

.card-accent {
  height: 4px;
}

.accent-once { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.accent-daily { background: linear-gradient(90deg, #6366f1, #818cf8); }
.accent-monthly { background: linear-gradient(90deg, #8b5cf6, #a78bfa); }
.accent-cron { background: linear-gradient(90deg, #ec4899, #f472b6); }
.accent-special { background: linear-gradient(90deg, #10b981, #34d399); }

.card-content {
  padding: 16px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.period-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s ease;
}

.task-card:active .period-icon {
  transform: scale(1.05);
}

.icon-once { background: var(--color-once-icon-bg); color: var(--color-once-icon); }
.icon-daily { background: var(--color-daily-icon-bg); color: var(--color-daily-icon); }
.icon-monthly { background: var(--color-monthly-icon-bg); color: var(--color-monthly-icon); }
.icon-cron { background: var(--color-cron-icon-bg); color: var(--color-cron-icon); }
.icon-special { background: var(--color-special-icon-bg); color: var(--color-special-icon); }

.period-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: var(--radius-round);
  letter-spacing: 0.3px;
}

.tag-once { background: var(--color-once-bg); color: var(--color-once-text); }
.tag-daily { background: var(--color-daily-bg); color: var(--color-daily-text); }
.tag-monthly { background: var(--color-monthly-bg); color: var(--color-monthly-text); }
.tag-cron { background: var(--color-cron-bg); color: var(--color-cron-text); }
.tag-special { background: var(--color-special-bg); color: var(--color-special-text); }

.card-body {
  margin-bottom: 12px;
}

.card-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.3;
  letter-spacing: -0.2px;
}

.card-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 12px;
}

.card-footer-line {
  flex: 1;
  height: 2px;
  border-radius: 1px;
  opacity: 0.5;
}

.line-ONCE { background: linear-gradient(90deg, #f59e0b, transparent); }
.line-DAILY { background: linear-gradient(90deg, #6366f1, transparent); }
.line-MONTHLY { background: linear-gradient(90deg, #8b5cf6, transparent); }
.line-CRON { background: linear-gradient(90deg, #ec4899, transparent); }
.line-SPECIAL { background: linear-gradient(90deg, #10b981, transparent); }

.card-action {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.action-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-brand);
}

.card-action svg {
  color: var(--color-brand);
  transition: transform 0.2s ease;
}

.task-card:active .card-action svg {
  transform: translateX(2px);
}
</style>
