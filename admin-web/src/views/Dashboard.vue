<template>
  <div class="dashboard">
    <h2>运营仪表盘</h2>
    <el-row :gutter="16" class="card-row">
      <el-col :span="6">
        <el-card class="metric-card">
          <div class="metric-value">{{ today.views }}</div>
          <div class="metric-label">今日曝光</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card">
          <div class="metric-value">{{ today.participants }}</div>
          <div class="metric-label">今日参与</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card">
          <div class="metric-value">{{ today.completions }}</div>
          <div class="metric-label">今日完成</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card">
          <div class="metric-value">{{ rewardRate }}%</div>
          <div class="metric-label">发奖成功率</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card class="table-card">
      <template #header><span>Top 10 任务指标</span></template>
      <el-table :data="topTasks" stripe v-loading="loading">
        <el-table-column prop="taskId" label="任务 ID" width="100" align="center">
          <template #default="{ row }">
            <span class="task-link" @click="router.push(`/tasks/${row.taskId}/metrics`)">
              #{{ row.taskId }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="views" label="曝光" />
        <el-table-column prop="participants" label="参与" />
        <el-table-column prop="completions" label="完成" />
        <el-table-column prop="rewardSuccess" label="发奖成功" />
        <el-table-column prop="rewardFailure" label="发奖失败" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Dashboard' })
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDashboard, type TaskMetrics } from '../api/metrics'

const router = useRouter()

const today = ref<TaskMetrics>({
  id: 0,
  taskId: 0,
  metricDate: '',
  views: 0,
  participants: 0,
  completions: 0,
  rewardSuccess: 0,
  rewardFailure: 0,
  avgFilterMs: 0,
})
const topTasks = ref<TaskMetrics[]>([])
const loading = ref(false)

const rewardRate = computed(() => {
  const total = today.value.rewardSuccess + today.value.rewardFailure
  if (total === 0) return '0.0'
  return ((today.value.rewardSuccess / total) * 100).toFixed(1)
})

onMounted(async () => {
  loading.value = true
  try {
    const { data: res } = await getDashboard()
    if (res?.data) {
      today.value = res.data.today || today.value
      topTasks.value = res.data.topTasks || []
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载仪表盘数据失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.card-row {
  margin-bottom: 16px;
}
.metric-card {
  text-align: center;
}
.metric-value {
  font-size: 32px;
  font-weight: bold;
  color: var(--color-brand-primary);
}
.metric-label {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-top: 4px;
}
.table-card {
  margin-top: 16px;
}
.task-link {
  color: var(--color-brand-primary);
  font-weight: 600;
  cursor: pointer;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
  transition: all 0.15s;
}
.task-link:hover {
  background: var(--color-brand-primary-subtle);
  color: var(--color-brand-primary-hover);
  text-decoration: underline;
}
</style>
