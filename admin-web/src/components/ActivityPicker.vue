<template>
  <el-select
    :model-value="modelValue"
    @update:model-value="$emit('update:model-value', $event)"
    filterable
    clearable
    :placeholder="placeholder || '选择关联活动（可选）'"
    :loading="loading"
    style="width: 100%"
  >
    <template #prefix>
      <el-icon v-if="modelValue" style="color: var(--el-color-primary)"><Connection /></el-icon>
    </template>
    <el-option
      v-for="item in activities"
      :key="item.code"
      :label="item.code"
      :value="item.code"
      class="activity-option"
    >
      <div class="option-main">
        <div class="option-header">
          <span class="option-code">{{ item.code }}</span>
          <el-tag :type="statusType(item.status)" size="small" effect="plain" class="option-tag">
            {{ statusLabel(item.status) }}
          </el-tag>
        </div>
        <div class="option-name">{{ item.name }}</div>
      </div>
    </el-option>
    <template #empty>
      <div class="picker-empty">
        <span v-if="loading">加载中...</span>
        <span v-else>暂无活动数据</span>
      </div>
    </template>
  </el-select>
</template>

<script setup lang="ts">
defineOptions({ name: 'ActivityPicker' })
import { onMounted, ref } from 'vue'
import { Connection } from '@element-plus/icons-vue'
import { listActivities, type Activity } from '../api/activity'

const props = defineProps<{
  modelValue?: string
  placeholder?: string
}>()

defineEmits<{
  'update:model-value': [value: string | undefined]
}>()

const activities = ref<Activity[]>([])
const loading = ref(false)

function statusType(status?: string) {
  switch (status) {
    case 'PUBLISHED': return 'success'
    case 'OFFLINE': return 'info'
    default: return 'warning'
  }
}

function statusLabel(status?: string) {
  switch (status) {
    case 'PUBLISHED': return '已发布'
    case 'OFFLINE': return '已下线'
    case 'DRAFT': return '草稿'
    default: return status || '未知'
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await listActivities({ page: 1, size: 1000 })
    activities.value = data.data?.records ?? []
  } catch {
    // silently ignore — select will just be empty
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.activity-option {
  padding: 8px 12px !important;
  height: auto !important;
}
.option-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.option-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.option-code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.option-tag {
  transform: scale(0.9);
  transform-origin: left center;
}
.option-name {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.3;
}
.picker-empty {
  text-align: center;
  padding: 12px 0;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>
