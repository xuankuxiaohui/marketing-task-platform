<template>
  <div class="task-detail-page">
    <van-nav-bar title="任务详情" left-arrow @click-left="$router.back()" />

    <van-loading v-if="loading" size="24px" style="margin-top: 40px">加载中...</van-loading>

    <template v-else-if="detail">
      <div class="task-header">
        <h2>{{ taskName }}</h2>
        <van-tag :type="statusTagType">{{ statusText }}</van-tag>
      </div>

      <van-steps :active="activeStep" direction="vertical" active-icon="success" active-color="#38f">
        <van-step v-for="step in detail.steps" :key="step.id">
          <h3>{{ step.name || step.code }}</h3>
          <p>{{ step.flowDesc || '' }}</p>
        </van-step>
      </van-steps>

      <div v-if="currentClickStep" class="action-bar">
        <van-button type="primary" block round @click="handleClick" :loading="clicking">
          {{ currentClickStep.buttonText || '完成此步骤' }}
        </van-button>
      </div>
    </template>

    <van-empty v-else description="任务不存在" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { showToast } from 'vant'
import { getTaskDetail, clickStep, type TaskInstanceDetail } from '../../api/task'

const route = useRoute()
const taskId = Number(route.params.id)
const detail = ref<TaskInstanceDetail | null>(null)
const loading = ref(false)
const clicking = ref(false)

const activeStep = computed(() => {
  if (!detail.value) return -1
  return Math.max(0, (detail.value.instance.currentStepSeq || 1) - 1)
})

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

onMounted(loadDetail)
</script>

<style scoped>
.task-detail-page {
  min-height: 100vh;
  background: #f7f8fa;
}
.task-header {
  padding: 16px;
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.task-header h2 {
  margin: 0;
  font-size: 18px;
}
.action-bar {
  padding: 16px;
  position: sticky;
  bottom: 0;
  background: #fff;
}
</style>
