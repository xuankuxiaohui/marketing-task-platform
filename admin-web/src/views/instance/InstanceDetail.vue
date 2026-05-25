<template>
  <div class="instance-detail-page" v-loading="loading" element-loading-text="加载中...">
    <div class="detail-header-bar">
      <el-button @click="$router.push('/instances')" text>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" style="margin-right:4px"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        返回列表
      </el-button>
      <span class="header-title" v-if="detail">实例详情 #{{ detail.instance.id }}</span>
    </div>

    <template v-if="detail">
      <el-row :gutter="16">
        <el-col :span="14">
          <el-card class="section-card">
            <template #header><span class="card-title">基本信息</span></template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="实例 ID">{{ detail.instance.id }}</el-descriptions-item>
              <el-descriptions-item label="用户 ID">{{ detail.instance.userId }}</el-descriptions-item>
              <el-descriptions-item label="任务名称">
                <template v-if="detail.instance.taskName">
                  <span class="task-link" @click="$router.push(`/tasks/${detail.instance.taskId}`)">
                    {{ detail.instance.taskName }}
                  </span>
                </template>
                <span v-else>任务 #{{ detail.instance.taskId }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="任务版本">v{{ detail.instance.taskVersion }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <span :class="['status-pill', statusClass(detail.instance.status)]">
                  {{ statusLabel(detail.instance.status) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="周期">{{ detail.instance.cycleKey }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatTime(detail.instance.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ formatTime(detail.instance.startTime) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ detail.instance.completeTime ? formatTime(detail.instance.completeTime) : '--' }}</el-descriptions-item>
              <el-descriptions-item label="发奖时间">{{ detail.instance.rewardTime ? formatTime(detail.instance.rewardTime) : '--' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <el-col :span="10">
          <el-card class="section-card">
            <template #header>
              <span class="card-title">
                步骤进度
                <span class="step-count-badge">{{ completedSteps }}/{{ detail.totalSteps }}</span>
              </span>
            </template>
            <div class="step-timeline">
              <div v-for="(step, idx) in detail.steps" :key="step.stepId"
                :class="['step-item', stepClass(step.status)]">
                <div class="step-connector">
                  <div :class="['step-dot', stepClass(step.status)]">
                    <svg v-if="step.status === 'COMPLETED'" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                    <span v-else class="step-num">{{ step.stepSeq }}</span>
                  </div>
                  <div v-if="idx < detail.steps.length - 1" :class="['step-line', stepClass(step.status)]" />
                </div>
                <div class="step-content">
                  <div class="step-header-inline">
                    <span class="step-name">{{ step.stepName }}</span>
                    <span :class="['step-type-tag', typeClass(step.stepType)]">{{ typeLabel(step.stepType) }}</span>
                    <span :class="['step-status-tag', stepClass(step.status)]">{{ stepStatusLabel(step.status) }}</span>
                  </div>
                  <div class="step-body">
                    <template v-if="step.stepType === 'PROGRESS' && step.targetValue">
                      <div class="progress-bar-wrap">
                        <div class="progress-bar">
                          <div class="progress-fill" :style="{ width: progressPercent(step) + '%' }" />
                        </div>
                        <span class="progress-text">{{ step.progressValue ?? 0 }} / {{ step.targetValue }}</span>
                      </div>
                    </template>
                    <template v-if="step.stepType === 'REWARD'">
                      <span :class="['reward-status-tag', stepClass(step.status)]">
                        {{ rewardStatusLabel(step.status) }}
                      </span>
                    </template>
                    <template v-if="step.completeTime">
                      <span class="step-time">{{ formatTime(step.completeTime) }}</span>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="section-card event-log-card">
        <template #header><span class="card-title">事件日志</span></template>
        <div v-if="events.length === 0" class="empty-events">暂无事件记录</div>
        <div v-else class="event-timeline">
          <div v-for="event in events" :key="event.id" class="event-item">
            <div class="event-connector">
              <div class="event-dot" />
              <div class="event-line" />
            </div>
            <div class="event-content">
              <div class="event-header">
                <span class="event-type-tag">{{ event.eventType }}</span>
                <span class="event-time">{{ formatTime(event.createdAt) }}</span>
              </div>
              <div class="event-detail" v-if="event.eventData">
                <template v-if="event.stepId">
                  <span class="event-field">步骤ID:</span> {{ event.stepId }}
                </template>
                <template v-if="event.platform">
                  <span class="event-field">平台:</span> {{ event.platform }}
                </template>
                <span v-if="event.eventData" class="event-data">{{ event.eventData }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </template>

    <el-result v-else-if="!loading" icon="error" title="加载失败" sub-title="无法加载实例详情">
      <template #extra>
        <el-button type="primary" @click="$router.push('/instances')">返回列表</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInstanceDetail, getInstanceEvents, type InstanceDetail } from '../../api/instance'

const route = useRoute()
const loading = ref(false)
const detail = ref<InstanceDetail | null>(null)
const events = ref<Array<{
  id: number; eventType: string; taskId: number; instanceId: number;
  stepId: number; userId: string; platform: string; eventData: string; createdAt: string
}>>([])

const completedSteps = computed(() =>
  detail.value ? detail.value.steps.filter(s => s.status === 'COMPLETED').length : 0
)

const statusLabel = (s: string) => ({
  PENDING: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成',
  REWARDED: '已奖励', EXPIRED: '已过期',
}[s] || s)

const statusClass = (s: string) => ({
  PENDING: 'pending', IN_PROGRESS: 'in-progress', COMPLETED: 'completed',
  REWARDED: 'rewarded', EXPIRED: 'expired',
}[s] || '')

const typeLabel = (t: string) => ({
  CLICK: '点击', CALLBACK: '回调', PROGRESS: '进度',
  REWARD: '奖励', PASSIVE: '被动',
}[t] || t)

const typeClass = (t: string) => ({
  CLICK: 'type-click', CALLBACK: 'type-callback', PROGRESS: 'type-progress',
  REWARD: 'type-reward', PASSIVE: 'type-passive',
}[t] || '')

const stepStatusLabel = (s: string) => ({
  PENDING: '待完成', IN_PROGRESS: '进行中', COMPLETED: '已完成', FAILED: '已失败',
}[s] || s)

const stepClass = (s: string) => ({
  PENDING: 'step-pending', IN_PROGRESS: 'step-in-progress',
  COMPLETED: 'step-completed', FAILED: 'step-failed',
}[s] || 'step-pending')

const rewardStatusLabel = (s: string) => ({
  PENDING: '奖励待发放', COMPLETED: '奖励已发放', FAILED: '奖励发放失败',
}[s] || s)

const progressPercent = (step: { progressValue: number | null; targetValue: number | null }) => {
  if (!step.targetValue || step.targetValue === 0) return 0
  return Math.min(Math.round(((step.progressValue ?? 0) / step.targetValue) * 100), 100)
}

const formatTime = (t: string | null | undefined) => {
  if (!t) return '--'
  return t.replace('T', ' ').substring(0, 19)
}

async function loadDetail() {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    const { data: detailData } = await getInstanceDetail(id)
    detail.value = detailData.data

    const { data: eventsData } = await getInstanceEvents(id)
    events.value = eventsData.data || []
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载实例详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.instance-detail-page { max-width: 1200px; }
.detail-header-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.header-title { font-size: 16px; font-weight: 700; color: var(--color-text-primary); }
.section-card { margin-bottom: 16px; }
.card-title { font-size: 14px; font-weight: 700; color: var(--color-text-primary); display: flex; align-items: center; gap: 8px; }
.step-count-badge { display: inline-flex; align-items: center; padding: 1px 8px; background: var(--color-brand-primary-subtle); color: var(--color-brand-primary); border-radius: 10px; font-size: 11px; font-weight: 600; }
.task-link { color: var(--color-brand-primary); font-weight: 600; cursor: pointer; font-size: 13px; transition: all 0.15s; }
.task-link:hover { color: var(--color-brand-primary-hover); text-decoration: underline; }

.status-pill { display: inline-flex; align-items: center; gap: 5px; padding: 2px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; line-height: 1.6; }
.status-pill::before { content: ''; width: 6px; height: 6px; border-radius: 50%; }
.status-pill.pending { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.status-pill.pending::before { background: var(--color-warning); }
.status-pill.in-progress { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.status-pill.in-progress::before { background: var(--color-brand-secondary); }
.status-pill.completed { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.status-pill.completed::before { background: var(--color-success); }
.status-pill.rewarded { background: var(--color-brand-primary-subtle); color: var(--color-brand-primary); }
.status-pill.rewarded::before { background: var(--color-brand-secondary); }
.status-pill.expired { background: var(--color-border-light); color: var(--color-text-muted); }
.status-pill.expired::before { background: var(--color-text-disabled); }

.step-timeline { padding: 4px 0; }
.step-item { display: flex; gap: 12px; min-height: 56px; }
.step-item:last-child { min-height: auto; }
.step-connector { display: flex; flex-direction: column; align-items: center; width: 28px; flex-shrink: 0; }
.step-dot { width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; flex-shrink: 0; transition: all 0.2s; }
.step-dot.step-pending { background: var(--color-border-light); color: var(--color-text-disabled); border: 2px solid var(--color-border); }
.step-dot.step-in-progress { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); border: 2px solid var(--color-brand-secondary); }
.step-dot.step-completed { background: var(--color-emerald-subtle); color: var(--color-emerald-text); border: 2px solid var(--color-success); }
.step-dot.step-failed { background: #fef2f2; color: #dc2626; border: 2px solid #dc2626; }
.step-num { line-height: 1; }
.step-line { width: 2px; flex: 1; min-height: 16px; margin: 4px 0; transition: background 0.2s; }
.step-line.step-pending { background: var(--color-border); }
.step-line.step-in-progress { background: var(--el-color-primary-light-5); }
.step-line.step-completed { background: var(--color-emerald-light); }
.step-line.step-failed { background: #fecaca; }
.step-content { flex: 1; padding: 0 0 12px 0; min-width: 0; }
.step-header-inline { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 4px; }
.step-name { font-weight: 600; font-size: 13px; color: var(--color-text-primary); }
.step-type-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600; }
.type-click { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.type-callback { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.type-progress { background: var(--color-brand-primary-subtle); color: var(--color-brand-primary); }
.type-reward { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.type-passive { background: var(--color-pink-subtle); color: var(--color-pink-text); }
.step-status-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600; }
.step-status-tag.step-pending { background: var(--color-border-light); color: var(--color-text-muted); }
.step-status-tag.step-in-progress { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.step-status-tag.step-completed { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.step-status-tag.step-failed { background: #fef2f2; color: #dc2626; }
.step-body { margin-left: 0; }
.progress-bar-wrap { display: flex; align-items: center; gap: 8px; margin: 4px 0; }
.progress-bar { flex: 1; height: 6px; background: var(--color-border); border-radius: 3px; overflow: hidden; }
.progress-fill { height: 100%; background: linear-gradient(90deg, var(--color-brand-primary), var(--color-brand-secondary)); border-radius: 3px; transition: width 0.3s ease; }
.progress-text { font-size: 11px; color: var(--color-brand-primary); font-weight: 600; white-space: nowrap; }
.step-time { display: inline-flex; align-items: center; font-size: 11px; color: var(--color-text-muted); margin-top: 2px; }
.reward-status-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600; }
.reward-status-tag.step-pending { background: var(--color-border-light); color: var(--color-text-muted); }
.reward-status-tag.step-completed { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.reward-status-tag.step-failed { background: #fef2f2; color: #dc2626; }

.event-log-card { margin-top: 0; }
.empty-events { text-align: center; color: var(--color-text-muted); padding: 24px; font-size: 13px; }
.event-timeline { padding: 4px 0; }
.event-item { display: flex; gap: 12px; min-height: 48px; }
.event-item:last-child .event-line { display: none; }
.event-connector { display: flex; flex-direction: column; align-items: center; width: 16px; flex-shrink: 0; }
.event-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--color-brand-secondary); flex-shrink: 0; margin-top: 6px; }
.event-line { width: 2px; flex: 1; background: var(--color-border); margin: 4px 0; }
.event-content { flex: 1; padding: 0 0 12px 0; min-width: 0; }
.event-header { display: flex; align-items: center; gap: 8px; margin-bottom: 2px; }
.event-type-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600; background: var(--color-brand-primary-subtle); color: var(--color-brand-primary); }
.event-time { font-size: 11px; color: var(--color-text-muted); }
.event-detail { font-size: 12px; color: var(--color-text-secondary); margin-top: 2px; }
.event-field { color: var(--color-text-muted); font-weight: 500; margin-right: 4px; }
.event-data { display: block; color: var(--color-text-muted); font-size: 11px; margin-top: 2px; word-break: break-all; }
</style>
