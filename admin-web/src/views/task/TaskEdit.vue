<template>
  <el-card>
    <template #header>
      <div class="edit-header">
        <span>{{ isEdit ? '编辑任务' : '新建任务' }}</span>
        <span class="edit-sub">配置任务基本信息、步骤、过滤器和端入口</span>
      </div>
    </template>
    <el-tabs v-model="activeTab" class="edit-tabs">
      <el-tab-pane label="基本信息" name="basic">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            基本信息
          </span>
        </template>
        <BasicTab v-model="task" />
      </el-tab-pane>
      <el-tab-pane label="步骤配置" name="steps">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
            步骤配置
          </span>
        </template>
        <StepsTab ref="stepsTabRef" />
      </el-tab-pane>
      <el-tab-pane label="过滤器" name="filters">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/></svg>
            过滤器
          </span>
        </template>
        <FiltersTab ref="filtersTabRef" />
      </el-tab-pane>
      <el-tab-pane label="端配置" name="platforms">
        <template #label>
          <span class="tab-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            端配置
          </span>
        </template>
        <PlatformsTab ref="platformsTabRef" />
      </el-tab-pane>
    </el-tabs>
    <div class="form-actions">
      <el-button type="primary" @click="submit" :loading="submitting">保存草稿</el-button>
      <el-button @click="$router.push('/tasks')">取消</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { saveTaskAggregate, getTaskById, type Task } from '../../api/task'
import { listSteps } from '../../api/step'
import { listFilters } from '../../api/filter'
import { listPlatforms } from '../../api/platform'
import BasicTab from './tabs/BasicTab.vue'
import FiltersTab from './tabs/FiltersTab.vue'
import PlatformsTab from './tabs/PlatformsTab.vue'
import StepsTab from './tabs/StepsTab.vue'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => taskId.value !== null)
const submitting = ref(false)
const activeTab = ref('basic')

const task = ref<Task>({
  code: '',
  name: '',
  description: '',
  periodType: 'DAILY',
  status: 'DRAFT',
})

const stepsTabRef = ref()
const filtersTabRef = ref()
const platformsTabRef = ref()

onMounted(async () => {
  if (taskId.value) {
    try {
      const [{ data: taskResp }, { data: stepsResp }, { data: filtersResp }, { data: platformsResp }] = await Promise.all([
        getTaskById(taskId.value),
        listSteps(taskId.value),
        listFilters(taskId.value),
        listPlatforms(taskId.value),
      ])
      task.value = taskResp.data
      stepsTabRef.value?.setSteps(stepsResp.data)
      filtersTabRef.value?.setFilters(filtersResp.data)
      platformsTabRef.value?.setPlatforms(platformsResp.data)
    } catch (e) {
      console.error('Failed to load task data:', e)
    }
  }
})

async function submit() {
  submitting.value = true
  try {
    const dto = {
      task: task.value,
      steps: stepsTabRef.value?.getSteps(),
      filters: filtersTabRef.value?.getFilters(),
      platforms: platformsTabRef.value?.getPlatforms(),
    }
    await saveTaskAggregate(dto)
    ElMessage.success('保存成功')
    await router.push('/tasks')
  } catch (e: any) {
    const msg = e.response?.data?.message || '保存失败'
    ElMessage.error(msg)
    console.error('Failed to save task:', e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.edit-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.edit-header span:first-child {
  font-size: 16px;
  font-weight: 700;
  color: #2d1b69;
}
.edit-sub {
  font-size: 12px;
  color: #a78bfa;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.edit-tabs {
  margin-top: 4px;
}

.form-actions {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f5f3ff;
  display: flex;
  gap: 12px;
}
</style>
