<template>
  <div>
    <el-button type="primary" size="small" @click="addStep" style="margin-bottom: 12px">添加步骤</el-button>
    <el-table :data="steps" border>
      <el-table-column label="序号" width="80">
        <template #default="{ $index }">
          <span>{{ $index + 1 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="编码" width="160">
        <template #default="{ row }">
          <el-input v-model="row.code" placeholder="step_code" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="名称" width="160">
        <template #default="{ row }">
          <el-input v-model="row.name" placeholder="步骤名称" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="140">
        <template #default="{ row }">
          <el-select v-model="row.type" size="small">
            <el-option label="点击" value="CLICK" />
            <el-option label="回调" value="CALLBACK" />
            <el-option label="进度" value="PROGRESS" />
            <el-option label="奖励" value="REWARD" />
            <el-option label="被动" value="PASSIVE" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="描述">
        <template #default="{ row }">
          <el-input v-model="row.flowDesc" placeholder="流程描述" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="额外配置" width="200">
        <template #default="{ row }">
          <el-input-number v-if="row.type === 'PROGRESS'" v-model="row.targetValue" :min="1" size="small" placeholder="目标值" />
          <el-input v-else-if="row.type === 'CALLBACK'" v-model="row.callbackEventKey" placeholder="事件key" size="small" />
          <el-input v-else-if="row.type === 'REWARD'" v-model="row.rewardConfigJson" placeholder='{"type":"point"}' size="small" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button type="danger" size="small" @click="removeStep($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Step } from '../../../api/step'

const steps = ref<Step[]>([])

function addStep() {
  steps.value.push({
    seq: steps.value.length + 1,
    code: '',
    name: '',
    type: 'CLICK',
    flowDesc: '',
  })
}

function removeStep(index: number) {
  steps.value.splice(index, 1)
  steps.value.forEach((s, i) => s.seq = i + 1)
}

function setSteps(data: Step[]) {
  steps.value = data || []
}

defineExpose({ getSteps: () => steps.value, setSteps })
</script>
