<template>
  <div>
    <el-card class="sim-card">
      <template #header><span>模拟用户身份</span></template>
      <el-form :model="identityForm" inline>
        <el-form-item label="userId">
          <el-input v-model="identityForm.userId" />
        </el-form-item>
        <el-form-item label="province">
          <el-input v-model="identityForm.province" />
        </el-form-item>
        <el-form-item label="role">
          <el-input v-model="identityForm.role" />
        </el-form-item>
        <el-form-item label="level">
          <el-input-number v-model="identityForm.level" :min="1" />
        </el-form-item>
        <el-form-item label="platform">
          <el-select v-model="identityForm.platform">
            <el-option label="IOS" value="IOS" />
            <el-option label="ANDROID" value="ANDROID" />
            <el-option label="MINIAPP" value="MINIAPP" />
          </el-select>
        </el-form-item>
        <el-form-item label="tags">
          <el-input v-model="identityForm.tagsStr" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleImpersonate">模拟登录</el-button>
          <el-button @click="handleClear">退出模拟</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="sim-card">
      <template #header><span>CALLBACK 模拟</span></template>
      <el-form :model="callbackForm" inline>
        <el-form-item label="instanceId">
          <el-input-number v-model="callbackForm.instanceId" :min="1" />
        </el-form-item>
        <el-form-item label="eventKey">
          <el-input v-model="callbackForm.eventKey" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCallback">发送回调</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="sim-card">
      <template #header><span>PROGRESS 模拟</span></template>
      <el-form :model="progressForm" inline>
        <el-form-item label="instanceId">
          <el-input-number v-model="progressForm.instanceId" :min="1" />
        </el-form-item>
        <el-form-item label="stepId">
          <el-input-number v-model="progressForm.stepId" :min="1" />
        </el-form-item>
        <el-form-item label="progressValue">
          <el-input-number v-model="progressForm.progressValue" :min="0" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleProgress">上报进度</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="sim-card">
      <template #header><span>一键全流程测试</span></template>
      <el-button type="success" @click="handleFullFlow" :loading="fullFlowLoading">执行全流程</el-button>
      <div v-if="fullFlowResults.length" class="flow-results">
        <el-tag
          v-for="(r, i) in fullFlowResults"
          :key="i"
          :type="r.success ? 'success' : 'danger'"
          class="flow-tag"
        >
          {{ r.message }}
        </el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {
  impersonate,
  clearImpersonate,
  simulateCallback,
  simulateProgress,
  simulateFullFlow,
  type ImpersonateRequest,
} from '../../api/simulate'

const props = defineProps<{ taskId: number }>()

const identityForm = reactive({
  userId: '',
  province: '',
  role: '',
  level: 1,
  platform: 'IOS',
  tagsStr: '',
})

async function handleImpersonate() {
  try {
    const body: ImpersonateRequest = {
      userId: identityForm.userId,
      province: identityForm.province,
      role: identityForm.role,
      level: identityForm.level,
      platform: identityForm.platform,
      tags: identityForm.tagsStr ? identityForm.tagsStr.split(',').map((t) => t.trim()) : [],
    }
    await impersonate(body)
    ElMessage.success('模拟登录成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '模拟登录失败')
  }
}

async function handleClear() {
  try {
    await clearImpersonate()
    ElMessage.success('已退出模拟')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '退出模拟失败')
  }
}

const callbackForm = reactive({
  instanceId: 0,
  eventKey: '',
})

async function handleCallback() {
  try {
    await simulateCallback(callbackForm.instanceId, callbackForm.eventKey)
    ElMessage.success('回调发送成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '回调发送失败')
  }
}

const progressForm = reactive({
  instanceId: 0,
  stepId: 0,
  progressValue: 0,
})

async function handleProgress() {
  try {
    await simulateProgress(progressForm.instanceId, progressForm.stepId, progressForm.progressValue)
    ElMessage.success('进度上报成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '进度上报失败')
  }
}

const fullFlowLoading = ref(false)
const fullFlowResults = ref<{ success: boolean; message: string }[]>([])

async function handleFullFlow() {
  fullFlowLoading.value = true
  fullFlowResults.value = []
  try {
    const res = await simulateFullFlow(props.taskId)
    const data = (res as any).data?.data || (res as any).data
    if (Array.isArray(data)) {
      fullFlowResults.value = data.map((item: any) => ({
        success: item.success !== false,
        message: item.message || item.stepName || '未知',
      }))
    } else {
      fullFlowResults.value = [{ success: true, message: '全流程执行完成' }]
    }
    ElMessage.success('全流程测试完成')
  } catch (e: any) {
    fullFlowResults.value = [{ success: false, message: e.response?.data?.message || '执行失败' }]
    ElMessage.error(e.response?.data?.message || '全流程测试失败')
  } finally {
    fullFlowLoading.value = false
  }
}
</script>

<style scoped>
.sim-card {
  margin-bottom: 16px;
}
.flow-results {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.flow-tag {
  font-size: 12px;
}
</style>
