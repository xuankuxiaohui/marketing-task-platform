<template>
  <el-card>
    <template #header>
      <div>
        <span class="page-title">用户任务实例</span>
        <p class="page-sub">查看用户的任务执行进度和状态</p>
      </div>
    </template>
    <el-table :data="rows">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userId" label="用户 ID" min-width="120" />
      <el-table-column prop="taskId" label="任务 ID" width="90" />
      <el-table-column prop="cycleKey" label="周期" min-width="120">
        <template #default="{ row }">
          <code class="cycle-code">{{ row.cycleKey }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <span :class="['status-pill', instanceStatusClass(row.status)]">{{ instanceStatusLabel(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="currentStepSeq" label="当前步骤" width="90" align="center">
        <template #default="{ row }">
          <span class="step-badge">{{ row.currentStepSeq }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="rewardTime" label="发奖时间" min-width="160">
        <template #default="{ row }">
          <span v-if="row.rewardTime" class="reward-time">{{ row.rewardTime }}</span>
          <span v-else class="no-reward">--</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listInstances } from '../../api/instance'

const rows = ref([])

const instanceStatusLabel = (s: string) => ({
  PENDING: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成',
  REWARDED: '已奖励', EXPIRED: '已过期',
}[s] || s)

const instanceStatusClass = (s: string) => ({
  PENDING: 'pending', IN_PROGRESS: 'in-progress', COMPLETED: 'completed',
  REWARDED: 'rewarded', EXPIRED: 'expired',
}[s] || '')

async function load() {
  try {
    const { data } = await listInstances()
    rows.value = data.data.records
  } catch (e) {
    console.error('Failed to load instances:', e)
  }
}

onMounted(load)
</script>

<style scoped>
.page-title {
  font-size: 16px;
  font-weight: 700;
  color: #2d1b69;
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #a78bfa;
}

.cycle-code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 11px;
  background: #f5f3ff;
  color: #6d28d9;
  padding: 2px 6px;
  border-radius: 4px;
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
.status-pill.pending     { background: #fef3c7; color: #b45309; }
.status-pill.pending::before     { background: #f59e0b; }
.status-pill.in-progress { background: #dbeafe; color: #1d4ed8; }
.status-pill.in-progress::before { background: #3b82f6; }
.status-pill.completed   { background: #d1fae5; color: #047857; }
.status-pill.completed::before   { background: #10b981; }
.status-pill.rewarded    { background: #ede9fe; color: #6d28d9; }
.status-pill.rewarded::before    { background: #7c3aed; }
.status-pill.expired     { background: #f1f5f9; color: #64748b; }
.status-pill.expired::before     { background: #94a3b8; }

.step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
}
.reward-time {
  font-size: 12px;
  color: #374151;
}
.no-reward {
  color: #d4d4d8;
  font-size: 12px;
}
</style>
