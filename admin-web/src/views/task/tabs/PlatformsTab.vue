<template>
  <div>
    <el-checkbox-group v-model="enabledPlatforms">
      <el-checkbox v-for="p in allPlatforms" :key="p" :label="p" :value="p">{{ p }}</el-checkbox>
    </el-checkbox-group>
    <el-table :data="platformConfigs" border style="margin-top: 12px">
      <el-table-column prop="platform" label="平台" width="120" />
      <el-table-column label="按钮文字">
        <template #default="{ row }">
          <el-input v-model="row.buttonText" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="流程描述">
        <template #default="{ row }">
          <el-input v-model="row.flowDesc" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="跳转URI">
        <template #default="{ row }">
          <el-input v-model="row.jumpUri" size="small" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TaskPlatform } from '../../../api/platform'

const allPlatforms = ['WEB', 'ADMIN', 'IOS', 'ANDROID', 'MINIAPP']
const platformConfigs = ref<TaskPlatform[]>([])

const enabledPlatforms = computed({
  get: () => platformConfigs.value.filter(p => p.enabled !== false).map(p => p.platform),
  set: (platforms: string[]) => {
    for (const p of platforms) {
      if (!platformConfigs.value.find(c => c.platform === p)) {
        platformConfigs.value.push({ platform: p, enabled: true })
      }
    }
    platformConfigs.value = platformConfigs.value.filter(c => platforms.includes(c.platform))
  },
})

function setPlatforms(data: TaskPlatform[]) {
  platformConfigs.value = data || []
}

defineExpose({ getPlatforms: () => platformConfigs.value, setPlatforms })
</script>
