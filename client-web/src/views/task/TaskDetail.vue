<template>
  <div class="task-detail-page">
    <van-nav-bar title="任务详情" left-arrow right-text="退出" @click-left="$router.back()" @click-right="handleSwitchUser" />

    <van-loading v-if="loading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

    <template v-else-if="detail">
      <div class="detail-header">
        <div class="header-top">
          <h2 class="task-name">{{ taskName }}</h2>
          <van-tag :type="statusTagType" size="medium" round>{{ statusText }}</van-tag>
        </div>
        <div v-if="detail.instance.cycleKey" class="header-meta">
          周期: {{ detail.instance.cycleKey }}
        </div>
      </div>

      <div class="steps-section">
        <div class="section-title">任务进度</div>
        <div class="steps-wrapper">
          <div
            v-for="(step, idx) in detail.steps"
            :key="step.id"
            :class="['step-item', stepStatus(idx)]"
          >
            <div :class="['step-dot', stepDotStatus(idx)]">
              <svg v-if="idx < activeStep" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><polyline points="20 6 9 17 4 12"/></svg>
              <span v-else-if="idx === activeStep" class="step-num">{{ idx + 1 }}</span>
              <span v-else class="step-num">{{ idx + 1 }}</span>
            </div>
            <div class="step-line" v-if="idx < detail.steps.length - 1"></div>
            <div class="step-content">
              <div class="step-name">{{ step.name || step.code }}</div>
              <div class="step-desc" v-if="step.flowDesc">{{ step.flowDesc }}</div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="currentClickStep" class="action-bar">
        <van-button type="primary" block round size="large" @click="handleClick" :loading="clicking">
          {{ currentClickStep.buttonText || '完成此步骤' }}
        </van-button>
      </div>
      <div v-else-if="isComplete" class="action-bar">
        <van-button block round size="large" disabled>任务已完成</van-button>
      </div>
    </template>

    <van-empty v-else description="任务不存在" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
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

const statusTagType = computed(() => {
  const map: Record<string, 'warning' | 'primary' | 'success' | 'danger' | 'default'> = {
    PENDING: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success',
    REWARDED: 'success', EXPIRED: 'danger',
  }
  return map[detail.value?.instance?.status || ''] || 'default'
})

const stepStatus = (idx: number) => (idx < activeStep.value ? 'done' : idx === activeStep.value ? 'active' : '')
const stepDotStatus = (idx: number) => (idx < activeStep.value ? 'dot-done' : idx === activeStep.value ? 'dot-active' : 'dot-pending')

async function loadDetail() {
  loading.value = true
  try {
    const { data } = await getTaskDetail(taskId)
    detail.value = data.data
  } catch (e: any) {
    showToast(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleClick() {
  if (!currentClickStep.value) return
  clicking.value = true
  try {
    await clickStep(taskId, currentClickStep.value.id)
    showToast('操作成功')
    await loadDetail()
  } catch (e: any) {
    showToast(e.response?.data?.message || '操作失败')
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

.loading-wrap {
  margin-top: 60px;
}

/* Header */
.detail-header {
  background: var(--color-surface);
  padding: var(--space-4);
  margin-bottom: 10px;
}
.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-3);
}
.task-name {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  flex: 1;
}
.header-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-muted);
}

/* Steps */
.steps-section {
  background: var(--color-surface);
  padding: var(--space-4);
  margin-bottom: 80px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: var(--space-4);
}
.steps-wrapper {
  position: relative;
}
.step-item {
  display: flex;
  min-height: 44px;
  position: relative;
}
.step-line {
  position: absolute;
  left: 14px;
  top: 28px;
  width: 2px;
  height: calc(100% - 8px);
  background: var(--color-step-line);
}
.step-item.done + .step-line,
.step-item.active + .step-item .step-line {
  background: var(--color-step-line);
}
.step-item.done .step-line {
  background: var(--color-step-done);
}

/* Step dots */
.step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: var(--space-3);
  margin-top: 2px;
  z-index: 1;
}
.dot-done {
  background: var(--color-step-done);
  color: #fff;
}
.dot-active {
  background: var(--color-surface);
  border: 2px solid var(--color-step-active-ring);
  color: var(--color-step-active-ring);
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

.step-content {
  padding-bottom: var(--space-4);
}
.step-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  line-height: 28px;
}
.step-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 2px;
}
.step-item.done .step-content {
  opacity: 0.6;
}

/* Action bar */
.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: var(--space-3) var(--space-4);
  padding-bottom: max(12px, env(safe-area-inset-bottom));
  background: var(--color-surface);
  box-shadow: var(--shadow-up);
}
.action-bar :deep(.van-button--large) {
  height: 46px;
  font-size: 15px;
  border-radius: var(--radius-xl);
}
</style>
