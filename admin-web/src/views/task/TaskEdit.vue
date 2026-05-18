<template>
  <el-card>
    <template #header>{{ isEdit ? '编辑任务' : '新建任务' }}</template>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="basic"><BasicTab v-model="task" /></el-tab-pane>
      <el-tab-pane label="步骤配置" name="steps"><StepsTab ref="stepsTabRef" /></el-tab-pane>
      <el-tab-pane label="过滤器" name="filters"><FiltersTab ref="filtersTabRef" /></el-tab-pane>
      <el-tab-pane label="端配置" name="platforms"><PlatformsTab ref="platformsTabRef" /></el-tab-pane>
    </el-tabs>
    <div style="margin-top: 16px">
      <el-button type="primary" @click="submit" :loading="submitting">保存草稿</el-button>
      <el-button @click="$router.push('/tasks')">取消</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
    await router.push('/tasks')
  } finally {
    submitting.value = false
  }
}
</script>
