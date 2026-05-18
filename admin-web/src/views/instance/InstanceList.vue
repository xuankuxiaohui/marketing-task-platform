<template>
  <el-card>
    <template #header>用户任务实例</template>
    <el-table :data="rows" border>
      <el-table-column prop="id" label="ID" width="100" />
      <el-table-column prop="userId" label="用户" />
      <el-table-column prop="taskId" label="任务ID" />
      <el-table-column prop="cycleKey" label="周期" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="currentStepSeq" label="当前步骤" />
      <el-table-column prop="rewardTime" label="发奖时间" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listInstances } from '../../api/instance'

const rows = ref([])

async function load() {
  const { data } = await listInstances()
  rows.value = data.data.records
}

onMounted(load)
</script>
