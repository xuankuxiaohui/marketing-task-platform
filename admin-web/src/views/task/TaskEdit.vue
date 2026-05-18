<template>
  <el-card>
    <template #header>任务编辑</template>
    <el-tabs>
      <el-tab-pane label="基本信息"><BasicTab v-model="task" /></el-tab-pane>
      <el-tab-pane label="步骤配置"><StepsTab /></el-tab-pane>
      <el-tab-pane label="过滤器"><FiltersTab /></el-tab-pane>
      <el-tab-pane label="端配置"><PlatformsTab /></el-tab-pane>
    </el-tabs>
    <el-button type="primary" @click="submit">保存草稿</el-button>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { saveTask, type Task } from '../../api/task'
import BasicTab from './tabs/BasicTab.vue'
import FiltersTab from './tabs/FiltersTab.vue'
import PlatformsTab from './tabs/PlatformsTab.vue'
import StepsTab from './tabs/StepsTab.vue'

const router = useRouter()
const task = ref<Task>({
  code: '',
  name: '',
  description: '',
  periodType: 'DAILY',
  status: 'DRAFT',
})

async function submit() {
  await saveTask(task.value)
  await router.push('/tasks')
}
</script>
