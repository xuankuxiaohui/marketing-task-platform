<template>
  <div class="task-metrics">
    <h2>任务指标 - #{{ taskId }}</h2>
    <el-row :gutter="16" class="card-row">
      <el-col :span="8">
        <el-card class="metric-card">
          <div class="metric-value">{{ summary.views ?? '-' }}</div>
          <div class="metric-label">累计曝光</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="metric-card">
          <div class="metric-value">{{ summary.participants ?? '-' }}</div>
          <div class="metric-label">累计参与</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="metric-card">
          <div class="metric-value">{{ summary.completions ?? '-' }}</div>
          <div class="metric-label">累计完成</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>每日指标</span>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            @change="onDateChange"
          />
        </div>
      </template>
      <el-table :data="dailyMetrics" stripe v-loading="dailyLoading">
        <el-table-column prop="metricDate" label="日期" width="120" />
        <el-table-column prop="views" label="曝光" />
        <el-table-column prop="participants" label="参与" />
        <el-table-column prop="completions" label="完成" />
        <el-table-column prop="rewardSuccess" label="发奖成功" />
        <el-table-column prop="rewardFailure" label="发奖失败" />
        <el-table-column prop="avgFilterMs" label="平均过滤(ms)" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTaskSummary, getTaskDaily, type TaskMetrics } from '../api/metrics'

const route = useRoute()
const taskId = Number(route.params.id)

const summary = ref<Record<string, any>>({})
const dailyMetrics = ref<TaskMetrics[]>([])
const dailyLoading = ref(false)
const dateRange = ref<[string, string] | null>(null)

onMounted(async () => {
  try {
    const { data: res } = await getTaskSummary(taskId)
    if (res?.data) {
      summary.value = res.data
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载任务指标失败')
  }
})

async function onDateChange() {
  if (!dateRange.value) return
  dailyLoading.value = true
  try {
    const { data: res } = await getTaskDaily(taskId, from, to)
    if (res?.data) {
      dailyMetrics.value = res.data
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载每日指标失败')
  } finally {
    dailyLoading.value = false
  }
}
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
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
