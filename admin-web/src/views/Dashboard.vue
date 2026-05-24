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
      <el-table :data="topTasks" stripe>
        <el-table-column prop="taskId" label="任务 ID" width="100" />
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
import { ref, computed, onMounted } from 'vue'
import { getDashboard, type TaskMetrics } from '../api/metrics'

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

const rewardRate = computed(() => {
  const total = today.value.rewardSuccess + today.value.rewardFailure
  if (total === 0) return '0.0'
  return ((today.value.rewardSuccess / total) * 100).toFixed(1)
})

onMounted(async () => {
  try {
    const { data: res } = await getDashboard()
    if (res?.data) {
      today.value = res.data.today || today.value
      topTasks.value = res.data.topTasks || []
    }
  } catch (e) {
    console.error('Failed to load dashboard:', e)
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
  color: #409eff;
}
.metric-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.table-card {
  margin-top: 16px;
}
</style>
