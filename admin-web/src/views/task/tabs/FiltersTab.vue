<template>
  <div>
    <el-button type="primary" size="small" @click="addFilter" style="margin-bottom:12px">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
      添加过滤器
    </el-button>

    <div v-if="filters.length === 0" class="empty-hint">
      <p>尚未配置过滤器，所有用户均可看到此任务。</p>
    </div>

    <FilterCard
      v-for="(filter, index) in filters"
      :key="index"
      :filter="filter"
      :index="index"
      @remove="removeFilter(index)"
      @validate="validateFilterExpression(filter)"
    />

    <div v-for="(filter, index) in filters" :key="'v'+index">
      <span v-if="filter._validation" :class="filter._valid ? 'valid-msg' : 'invalid-msg'" style="display:block;padding-left:16px">
        {{ filter._valid ? '✓' : '✗' }} {{ filter._validation }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { validateFilter } from '../../../api/filter'
import type { TaskFilter } from '../../../api/filter'
import type { FilterVisualState } from './filter-builder/conditions'
import { parseExpression, generateExpression } from './filter-builder/conditions'
import FilterCard from './filter-builder/FilterCard.vue'

type FilterWithState = TaskFilter & {
  _validation?: string
  _valid?: boolean
  _visual?: FilterVisualState
}

const filters = ref<FilterWithState[]>([])

function addFilter() {
  filters.value.push({
    seq: filters.value.length + 1,
    expression: '',
    enabled: true,
    _visual: { mode: 'visual', logicOp: 'AND', conditions: [] },
  })
}

function removeFilter(index: number) {
  filters.value.splice(index, 1)
}

async function validateFilterExpression(filter: FilterWithState) {
  try {
    await validateFilter(filter.expression)
    filter._validation = '表达式校验通过'
    filter._valid = true
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    filter._validation = err.response?.data?.message || '表达式无效'
    filter._valid = false
  }
}

function setFilters(data: FilterWithState[]) {
  filters.value = (data || []).map(f => {
    if (!f._visual) {
      f._visual = parseExpression(f.expression || '')
    }
    return f
  })
}

defineExpose({
  getFilters: () => {
    return filters.value.map(f => {
      if (f._visual && f._visual.mode === 'visual') {
        f.expression = generateExpression(f._visual)
      }
      const { _validation, _valid, _visual, ...clean } = f
      return clean
    })
  },
  setFilters,
})
</script>

<style scoped>
.valid-msg { color: var(--color-success); font-size: 12px; font-weight: 500; }
.invalid-msg { color: var(--color-danger); font-size: 12px; }

.empty-hint {
  text-align: center;
  padding: 32px;
  color: var(--color-text-muted);
  font-size: 13px;
}
</style>
