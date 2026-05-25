<template>
  <div class="condition-row">
    <el-checkbox
      :model-value="condition.negated"
      size="small"
      class="negate-check"
      @change="emitPatch({ negated: $event as boolean })"
    >
      非
    </el-checkbox>

    <el-select
      :model-value="condition.functionName"
      placeholder="选择条件"
      filterable
      class="fn-select"
      @change="onFnChange($event as string)"
    >
      <el-option-group v-for="group in groupedFns" :key="group.label" :label="group.label">
        <el-option
          v-for="fn in group.fns"
          :key="fn.name"
          :label="fn.label"
          :value="fn.name"
        />
      </el-option-group>
    </el-select>

    <!-- string-array with predefined options (provinces, roles) -->
    <el-select
      v-if="currentDef?.paramType === 'string-array' && currentDef?.paramOptions"
      :model-value="condition.params"
      multiple
      filterable
      :placeholder="currentDef?.paramHint"
      class="param-select"
      @change="emitPatch({ params: $event as string[] })"
    >
      <el-option
        v-for="opt in currentDef!.paramOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>

    <!-- string-array without options: comma-separated input -->
    <el-input
      v-else-if="currentDef?.paramType === 'string-array'"
      :model-value="condition.params.join(', ')"
      :placeholder="currentDef!.paramHint"
      class="param-input"
      @change="onTagsChange"
    />

    <!-- string -->
    <el-input
      v-else-if="currentDef?.paramType === 'string'"
      :model-value="condition.params[0] || ''"
      :placeholder="currentDef!.paramHint"
      class="param-input"
      @input="emitPatch({ params: [($event as string).trim()] })"
    />

    <!-- slider for grayscale -->
    <div v-else-if="condition.functionName === 'inGrayPercent'" class="param-slider">
      <el-slider
        :model-value="Number(condition.params[0]) || 0"
        :max="100"
        :step="1"
        show-input
        size="small"
        @update:model-value="emitPatch({ params: [String($event)] })"
      />
    </div>

    <!-- number input -->
    <el-input-number
      v-else-if="currentDef?.paramType === 'number'"
      :model-value="Number(condition.params[0]) || 0"
      :placeholder="currentDef!.paramHint"
      size="small"
      class="param-number"
      @change="emitPatch({ params: [String($event ?? 0)] })"
    />

    <el-button type="danger" size="small" plain @click="$emit('remove')">删除</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FilterCondition, FilterFunctionDef } from './conditions'
import { getGroupedFunctions, getFunctionDef } from './conditions'

const props = defineProps<{
  condition: FilterCondition
}>()

const emit = defineEmits<{
  (e: 'update:condition', patch: Partial<FilterCondition>): void
  (e: 'remove'): void
}>()

const groupedFns = getGroupedFunctions()

const currentDef = computed<FilterFunctionDef | undefined>(() =>
  getFunctionDef(props.condition.functionName)
)

function emitPatch(patch: Partial<FilterCondition>) {
  emit('update:condition', patch)
}

function onFnChange(newName: string) {
  const def = getFunctionDef(newName)
  const params: string[] = def?.paramType === 'number' ? ['0'] : []
  emitPatch({ functionName: newName, params, negated: false })
}

function onTagsChange(value: string | number) {
  const tags = String(value)
    .split(',')
    .map(s => s.trim())
    .filter(Boolean)
  emitPatch({ params: tags })
}
</script>

<style scoped>
.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.negate-check {
  flex-shrink: 0;
  margin-right: 4px;
}

.fn-select {
  width: 150px;
  flex-shrink: 0;
}

.param-select {
  min-width: 200px;
  flex: 1;
}

.param-input {
  min-width: 160px;
  flex: 1;
}

.param-slider {
  min-width: 220px;
  flex: 1;
}

.param-number {
  width: 140px;
  flex-shrink: 0;
}
</style>
