<template>
  <div>
    <el-button type="primary" size="small" @click="addStep" style="margin-bottom:12px">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
      添加步骤
    </el-button>
    <el-table :data="steps" class="steps-table">
      <el-table-column label="序号" width="70" align="center">
        <template #default="{ $index }">
          <span class="step-seq">{{ $index + 1 }}</span>
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
      <el-table-column label="类型" width="130">
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
      <el-table-column label="描述" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.flowDesc" placeholder="流程描述" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="额外配置" width="200">
        <template #default="{ row }">
          <el-input-number v-if="row.type === 'PROGRESS'" v-model="row.targetValue" :min="1" size="small" placeholder="目标值" />
          <el-input v-else-if="row.type === 'CALLBACK'" v-model="row.callbackEventKey" placeholder="事件 key" size="small" />
          <el-input v-else-if="row.type === 'REWARD'" v-model="row.rewardConfigJson" placeholder='{"type":"point"}' size="small" />
          <span v-else class="no-config">--</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ $index }">
          <el-button type="danger" size="small" plain @click="removeStep($index)">删除</el-button>
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

<style scoped>
.step-seq {
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
.no-config {
  color: #d4d4d8;
  font-size: 12px;
}
.steps-table {
  border-radius: 8px;
  overflow: hidden;
}
</style>
