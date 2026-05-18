<template>
  <div class="task-list-page">
    <van-nav-bar title="任务中心" right-text="切换用户" @click-right="$router.push('/login')" />

    <van-pull-refresh v-model="refreshing" @refresh="loadTasks">
      <van-loading v-if="loading" size="24px" style="margin-top: 40px">加载中...</van-loading>

      <van-empty v-else-if="tasks.length === 0" description="暂无可用任务" />

      <van-cell-group v-else inset>
        <van-cell
          v-for="task in tasks"
          :key="task.id"
          :title="task.name"
          :label="task.description || task.periodType"
          is-link
          @click="$router.push(`/task/${task.id}`)"
        >
          <template #extra>
            <van-tag :type="task.periodType === 'ONCE' ? 'primary' : 'success'">
              {{ task.periodType }}
            </van-tag>
          </template>
        </van-cell>
      </van-cell-group>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { showToast } from 'vant'
import { listTasks, type Task } from '../../api/task'

const tasks = ref<Task[]>([])
const loading = ref(false)
const refreshing = ref(false)

async function loadTasks() {
  try {
    loading.value = true
    const { data } = await listTasks()
    tasks.value = data.data || []
  } catch (e: any) {
    showToast(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(loadTasks)
</script>

<style scoped>
.task-list-page {
  min-height: 100vh;
  background: #f7f8fa;
}
</style>
