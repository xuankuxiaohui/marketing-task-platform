<template>
  <el-card>
    <template #header>
      <div class="header">
        <span>任务管理</span>
        <el-button type="primary" @click="$router.push('/tasks/new')">新建任务</el-button>
      </div>
    </template>
    <el-table :data="rows" border>
      <el-table-column prop="id" label="ID" width="100" />
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="periodType" label="周期" width="120" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="version" label="版本" width="100" />
      <el-table-column label="操作" width="300">
        <template #default="scope">
          <el-button size="small" @click="$router.push(`/tasks/${scope.row.id}`)">编辑</el-button>
          <el-button size="small" @click="publish(scope.row.id)">发布</el-button>
          <el-button size="small" @click="offline(scope.row.id)">下线</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listTasks, offlineTask, publishTask } from '../../api/task'

const rows = ref([])

async function load() {
  const { data } = await listTasks()
  rows.value = data.data.records
}

async function publish(id: number) {
  await publishTask(id)
  await load()
}

async function offline(id: number) {
  await offlineTask(id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
