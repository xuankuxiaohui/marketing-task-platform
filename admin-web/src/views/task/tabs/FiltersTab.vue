<template>
  <div>
    <el-button type="primary" size="small" @click="addFilter" style="margin-bottom:12px">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
      添加过滤器
    </el-button>

    <div v-if="filters.length === 0" class="empty-hint">
      <p>尚未配置过滤器，所有用户均可看到此任务。</p>
    </div>

    <div v-for="(filter, index) in filters" :key="index" class="filter-card">
      <div class="filter-header">
        <span class="filter-label">过滤器 #{{ index + 1 }}</span>
        <div>
          <el-switch v-model="filter.enabled" size="small" active-text="启用" style="margin-right:12px" />
          <el-button type="danger" size="small" plain @click="removeFilter(index)">删除</el-button>
        </div>
      </div>
      <div class="filter-body">
        <el-input
          v-model="filter.expression"
          placeholder="例如：inProvince(['BJ']) &amp;&amp; levelGte(3)"
          class="filter-expr"
        >
          <template #prepend>表达式</template>
        </el-input>
        <el-input v-model="filter.description" placeholder="描述此过滤条件的用途" class="filter-desc" />
      </div>
      <div class="filter-footer">
        <el-button size="small" @click="validateFilterExpression(filter)">校验表达式</el-button>
        <span v-if="filter._validation" :class="filter._valid ? 'valid-msg' : 'invalid-msg'">
          {{ filter._valid ? '✓' : '✗' }} {{ filter._validation }}
        </span>
      </div>
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
    filter._validation = '表达式校验通过'
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

<style scoped>
.filter-card {
  border: 1px solid #ede9fe;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
  background: #fff;
  transition: box-shadow 0.2s;
}
.filter-card:hover {
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.06);
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.filter-label {
  font-weight: 600;
  font-size: 13px;
  color: #4c1d95;
}

.filter-body {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
.filter-expr {
  flex: 2;
}
.filter-desc {
  flex: 1;
}

.filter-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}
.valid-msg { color: #10b981; font-size: 12px; font-weight: 500; }
.invalid-msg { color: #ef4444; font-size: 12px; }

.empty-hint {
  text-align: center;
  padding: 32px;
  color: #a78bfa;
  font-size: 13px;
}
</style>
