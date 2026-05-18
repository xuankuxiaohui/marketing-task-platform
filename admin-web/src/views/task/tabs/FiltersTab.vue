<template>
  <div>
    <el-button type="primary" size="small" @click="addFilter" style="margin-bottom: 12px">添加过滤器</el-button>
    <div v-for="(filter, index) in filters" :key="index" style="margin-bottom: 12px; padding: 12px; border: 1px solid #dcdfe6; border-radius: 4px;">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-input v-model="filter.expression" placeholder="inProvince(['BJ']) && levelGte(3)" size="small" />
        </el-col>
        <el-col :span="6">
          <el-input v-model="filter.description" placeholder="描述" size="small" />
        </el-col>
        <el-col :span="3">
          <el-switch v-model="filter.enabled" active-text="启用" />
        </el-col>
        <el-col :span="3">
          <el-button type="danger" size="small" @click="removeFilter(index)">删除</el-button>
        </el-col>
      </el-row>
      <el-row style="margin-top: 8px">
        <el-button size="small" @click="validateFilterExpression(filter)">校验表达式</el-button>
        <span v-if="filter._validation" :style="{ color: filter._valid ? 'green' : 'red', marginLeft: '8px' }">
          {{ filter._validation }}
        </span>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { validateFilter } from '../../../api/filter'
import type { TaskFilter } from '../../../api/filter'

const filters = ref<(TaskFilter & { _validation?: string; _valid?: boolean })[]>([])

function addFilter() {
  filters.value.push({ seq: filters.value.length + 1, expression: '', enabled: true })
}

function removeFilter(index: number) {
  filters.value.splice(index, 1)
}

async function validateFilterExpression(filter: any) {
  try {
    await validateFilter(filter.expression)
    filter._validation = '表达式可用'
    filter._valid = true
  } catch (e: any) {
    filter._validation = e.response?.data?.message || '表达式无效'
    filter._valid = false
  }
}

function setFilters(data: any[]) {
  filters.value = data || []
}

defineExpose({ getFilters: () => filters.value, setFilters })
</script>
