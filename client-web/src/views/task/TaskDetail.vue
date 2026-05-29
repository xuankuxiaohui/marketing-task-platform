<template>
  <div class="task-detail-page">
    <div class="detail-hero">
      <div class="hero-bg-shape"></div>
      <div class="hero-content">
        <div class="hero-nav">
          <div class="nav-back" @click="$router.back()">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
          </div>
          <div class="nav-exit" @click="handleSwitchUser">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </div>
        </div>

        <div class="hero-info animate-in" v-if="detail">
          <div class="status-badge" :class="statusBadgeClass">
            <span class="status-dot"></span>
            {{ statusText }}
          </div>
          <h1 class="detail-title">{{ taskName }}</h1>
          <div class="detail-meta" v-if="detail.instance.cycleKey">
            周期: {{ detail.instance.cycleKey }}
          </div>
        </div>
      </div>
    </div>

    <van-loading v-if="loading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

    <template v-else-if="detail">
      <div class="progress-card animate-in animate-in-delay-1">
        <div class="progress-header">
          <span class="progress-title">任务进度</span>
          <span class="progress-count">{{ completedSteps }}/{{ detail.steps.length }}</span>
        </div>
        <div class="progress-bar-bg">
          <div class="progress-bar-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
      </div>

      <div class="steps-section">
        <div
          v-for="(step, idx) in detail.steps"
          :key="step.id"
          :class="['step-item', stepStatus(idx), 'animate-in', `animate-in-delay-${Math.min(idx + 2, 8)}`]"
        >
          <div class="step-timeline">
            <div :class="['step-dot', stepDotStatus(idx)]">
              <svg v-if="idx < activeStep" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><polyline points="20 6 9 17 4 12"/></svg>
              <span v-else class="step-num">{{ idx + 1 }}</span>
            </div>
            <div class="step-line" v-if="idx < detail.steps.length - 1" :class="{ 'line-done': idx < activeStep }"></div>
          </div>
          <div class="step-card">
            <div class="step-name">{{ step.name || step.code }}</div>
            <div class="step-desc" v-if="step.flowDesc">{{ step.flowDesc }}</div>
            <div class="step-status-tag" v-if="idx < activeStep">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><polyline points="20 6 9 17 4 12"/></svg>
              已完成
            </div>
            <div class="step-status-tag active-tag" v-else-if="idx === activeStep">进行中</div>
          </div>
        </div>
      </div>

      <div class="action-bar" v-if="currentClickStep || isComplete">
        <div class="action-bar-bg"></div>
        <div class="action-bar-content">
          <van-button
            v-if="currentClickStep"
            type="primary"
            block
            round
            size="large"
            @click="handleClick"
            :loading="clicking"
            class="action-btn"
          >
            {{ currentClickStep.buttonText || '完成此步骤' }}
          </van-button>
          <van-button v-else-if="isComplete" block round size="large" disabled class="action-btn done-btn">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="margin-right: 6px;"><polyline points="20 6 9 17 4 12"/></svg>
            任务已完成
          </van-button>
        </div>
      </div>
    </template>

    <van-empty v-else description="任务不存在" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from '../../utils/toast'
import { getTaskDetail, clickStep, type TaskInstanceDetail } from '../../api/task'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const taskId = Number(route.params.id)
const detail = ref<TaskInstanceDetail | null>(null)
const loading = ref(false)
const clicking = ref(false)

const activeStep = computed(() => {
  if (!detail.value) return -1
  return Math.max(0, (detail.value.instance.currentStepSeq || 1) - 1)
})

const completedSteps = computed(() => activeStep.value)

const progressPercent = computed(() => {
  if (!detail.value) return 0
  const total = detail.value.steps.length
  if (total === 0) return 0
  return Math.round((activeStep.value / total) * 100)
})

const isComplete = computed(() =>
  detail.value?.instance?.status === 'COMPLETED' || detail.value?.instance?.status === 'REWARDED'
)

const currentClickStep = computed(() => {
  if (!detail.value) return null
  const currentSeq = detail.value.instance.currentStepSeq
  const step = detail.value.steps.find(s => s.seq === currentSeq)
  if (!step || step.type !== 'CLICK') return null
  const platform = detail.value.stepPlatforms.find(p => p.stepId === step.id)
  return { ...step, buttonText: platform?.buttonText }
})

const taskName = computed(() => detail.value?.steps?.[0]?.name || '任务详情')

const statusText = computed(() => {
  const map: Record<string, string> = {
    PENDING: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成',
    REWARDED: '已奖励', EXPIRED: '已过期',
  }
  return map[detail.value?.instance?.status || ''] || detail.value?.instance?.status || ''
})

const statusBadgeClass = computed(() => {
  const map: Record<string, string> = {
    PENDING: 'badge-pending', IN_PROGRESS: 'badge-progress', COMPLETED: 'badge-success',
    REWARDED: 'badge-success', EXPIRED: 'badge-expired',
  }
  return map[detail.value?.instance?.status || ''] || 'badge-pending'
})

const stepStatus = (idx: number) => (idx < activeStep.value ? 'done' : idx === activeStep.value ? 'active' : '')
const stepDotStatus = (idx: number) => (idx < activeStep.value ? 'dot-done' : idx === activeStep.value ? 'dot-active' : 'dot-pending')

async function loadDetail() {
  loading.value = true
  try {
    const { data } = await getTaskDetail(taskId)
    detail.value = data.data
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleClick() {
  if (!currentClickStep.value) return
  clicking.value = true
  try {
    await clickStep(taskId, currentClickStep.value.id)
    showToast.success('操作成功')
    await loadDetail()
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '操作失败')
  } finally {
    clicking.value = false
  }
}

function handleSwitchUser() {
  userStore.logout()
  router.push('/login')
}

onMounted(loadDetail)
</script>

<style scoped>
.task-detail-page {
  min-height: 100vh;
  background: var(--color-bg);
}

/* ── Hero ── */
.detail-hero {
  position: relative;
  overflow: hidden;
}

.hero-bg-shape {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #4f46e5 0%, #6366f1 50%, #818cf8 100%);
}

.hero-content {
  position: relative;
  z-index: 1;
  padding: 0 20px 28px;
}

.hero-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0 20px;
}

.nav-back, .nav-exit {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-back:active, .nav-exit:active {
  transform: scale(0.92);
  background: rgba(255, 255, 255, 0.25);
}

.hero-info {
  color: #fff;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: var(--radius-round);
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 10px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
}

.badge-pending .status-dot { background: #fbbf24; }
.badge-progress .status-dot { background: #fff; animation: pulse-soft 2s infinite; }
.badge-success .status-dot { background: #34d399; }
.badge-expired .status-dot { background: rgba(255,255,255,0.4); }

.detail-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.3px;
}

.detail-meta {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.65;
}

/* ── Progress Card ── */
.progress-card {
  margin: -8px 16px 0;
  position: relative;
  z-index: 2;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 16px;
  box-shadow: var(--shadow-md);
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.progress-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.progress-count {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-brand);
}

.progress-bar-bg {
  height: 8px;
  background: var(--color-brand-subtle);
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: var(--color-brand-gradient);
  border-radius: 4px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.progress-bar-fill::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.3) 50%, transparent 100%);
  background-size: 200% 100%;
  animation: shimmer 2s infinite;
}

/* ── Steps ── */
.steps-section {
  padding: 16px 16px 100px;
}

.step-item {
  display: flex;
  gap: 14px;
}

.step-timeline {
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
  flex-shrink: 0;
  z-index: 1;
  transition: all 0.3s ease;
}

.dot-done {
  background: var(--color-step-done);
  color: #fff;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

.dot-active {
  background: var(--color-surface);
  border: 2.5px solid var(--color-step-active-ring);
  color: var(--color-step-active-ring);
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.1);
}

.dot-pending {
  background: var(--color-step-pending-bg);
  border: 2px solid var(--color-step-line);
  color: var(--color-text-muted);
}

.step-num {
  font-size: 12px;
  font-weight: 700;
}

.step-line {
  width: 2px;
  flex: 1;
  min-height: 20px;
  background: var(--color-step-line);
  transition: background 0.3s ease;
}

.step-line.line-done {
  background: var(--color-step-done);
}

.step-card {
  flex: 1;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: var(--shadow-sm);
  transition: all 0.3s ease;
}

.step-item.active .step-card {
  border: 1.5px solid var(--color-brand-subtle);
  box-shadow: 0 2px 12px rgba(99, 102, 241, 0.08);
}

.step-item.done .step-card {
  opacity: 0.65;
}

.step-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.4;
}

.step-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
  line-height: 1.5;
}

.step-status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-step-done);
  background: var(--color-brand-subtle);
  padding: 3px 10px;
  border-radius: var(--radius-round);
}

.step-status-tag.active-tag {
  color: var(--color-brand);
}

/* ── Action Bar ── */
.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 50;
}

.action-bar-bg {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

.action-bar-content {
  position: relative;
  z-index: 1;
  padding: 12px 20px;
  padding-bottom: max(12px, env(safe-area-inset-bottom));
}

.action-btn {
  height: 50px !important;
  font-size: 16px !important;
  font-weight: 700 !important;
  border-radius: 16px !important;
  letter-spacing: 1px;
  box-shadow: var(--shadow-brand) !important;
}

.done-btn {
  background: var(--color-status-success-bg) !important;
  color: var(--color-status-success-text) !important;
  box-shadow: none !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.loading-wrap {
  margin-top: 60px;
}
</style>
