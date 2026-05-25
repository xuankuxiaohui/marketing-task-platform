<template>
  <div class="filter-card">
    <div class="filter-header">
      <span class="filter-label">过滤器 #{{ index + 1 }}</span>
      <div class="header-actions">
        <el-switch v-model="filter.enabled" size="small" active-text="启用" />
        <el-button size="small" text type="primary" @click="toggleMode">
          {{ visual.mode === 'visual' ? '原始表达式' : '可视化' }}
        </el-button>
        <el-button type="danger" size="small" plain @click="$emit('remove')">删除</el-button>
      </div>
    </div>

    <!-- Visual mode -->
    <template v-if="visual.mode === 'visual'">
      <div v-if="visual.conditions.length === 0" class="empty-conditions">
        <p>点击下方按钮添加过滤条件</p>
      </div>

      <FilterConditionRow
        v-for="(cond, ci) in visual.conditions"
        :key="cond.id"
        :condition="cond"
        @update:condition="onConditionUpdate(ci, $event)"
        @remove="visual.conditions.splice(ci, 1)"
      />

      <div v-if="visual.conditions.length > 1" class="logic-op-row">
        <el-radio-group v-model="visual.logicOp" size="small">
          <el-radio-button value="AND">AND（并且）</el-radio-button>
          <el-radio-button value="OR">OR（或者）</el-radio-button>
        </el-radio-group>
      </div>

      <el-button size="small" text type="primary" class="add-cond-btn" @click="addCondition">
        + 添加条件
      </el-button>

      <div v-if="visual.conditions.length > 0" class="filter-summary">
        <span class="summary-label">预览：</span>
        <span class="summary-text">{{ summaryText }}</span>
      </div>

      <div class="generated-expr">
        <span class="expr-label">表达式：</span>
        <code>{{ filter.expression || '(空)' }}</code>
      </div>
    </template>

    <!-- Raw mode -->
    <template v-else>
      <el-input
        v-model="filter.expression"
        type="textarea"
        :rows="2"
        placeholder="直接输入 QLExpress 表达式，如 inProvince(['BJ']) &amp;&amp; levelGte(3)"
        class="raw-expr"
      />
      <p class="raw-hint">复杂表达式暂不支持可视化编辑，请直接输入 QLExpress 表达式</p>
    </template>

    <div class="filter-footer">
      <el-input
        v-model="filter.description"
        placeholder="描述此过滤条件的用途"
        class="filter-desc"
      />
      <el-button size="small" @click="$emit('validate')">校验表达式</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import type { TaskFilter } from '../../../../api/filter'
import type { FilterCondition, FilterVisualState } from './conditions'
import { generateExpression, parseExpression, generateSummary } from './conditions'
import FilterConditionRow from './FilterConditionRow.vue'

const props = defineProps<{
  filter: TaskFilter & { _visual?: FilterVisualState }
  index: number
}>()

defineEmits<{
  (e: 'remove'): void
  (e: 'validate'): void
}>()

if (!props.filter._visual) {
  props.filter._visual = parseExpression(props.filter.expression || '')
}
const visual = props.filter._visual

const summaryText = computed(() => generateSummary(visual.conditions, visual.logicOp))

watch(
  () => [visual.conditions, visual.logicOp],
  () => {
    if (visual.mode === 'visual') {
      props.filter.expression = generateExpression(visual)
    }
  },
  { deep: true }
)

function addCondition() {
  visual.conditions.push({
    id: crypto.randomUUID(),
    functionName: '',
    negated: false,
    params: [],
  })
}

function onConditionUpdate(index: number, patch: Partial<FilterCondition>) {
  const existing = visual.conditions[index]
  if (existing) {
    visual.conditions[index] = { ...existing, ...patch }
  }
}

function toggleMode() {
  if (visual.mode === 'visual') {
    props.filter.expression = generateExpression(visual)
    visual.mode = 'raw'
  } else {
    const parsed = parseExpression(props.filter.expression || '')
    visual.mode = parsed.mode
    visual.logicOp = parsed.logicOp
    visual.conditions = parsed.conditions
  }
}
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-weight: 600;
  font-size: 13px;
  color: #4c1d95;
}

.empty-conditions {
  text-align: center;
  padding: 16px;
  color: #a78bfa;
  font-size: 13px;
  background: #faf5ff;
  border-radius: 8px;
  margin-bottom: 8px;
}

.logic-op-row {
  margin: 8px 0;
  padding-left: 4px;
}

.add-cond-btn {
  margin-top: 4px;
}

.filter-summary {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  font-size: 13px;
}

.summary-label {
  color: #15803d;
  font-weight: 600;
}

.summary-text {
  color: #166534;
}

.generated-expr {
  margin-top: 6px;
  padding: 6px 12px;
  background: #f5f5f5;
  border-radius: 6px;
  font-size: 12px;
}

.expr-label {
  color: #737373;
  margin-right: 4px;
}

.generated-expr code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  color: #404040;
  font-size: 12px;
  word-break: break-all;
}

.raw-expr {
  margin-bottom: 4px;
}

.raw-hint {
  font-size: 12px;
  color: #a78bfa;
  margin: 4px 0 0 0;
}

.filter-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f3e8ff;
}

.filter-desc {
  flex: 1;
}
</style>
