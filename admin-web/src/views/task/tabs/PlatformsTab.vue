<template>
  <div>
    <div class="platform-section">
      <h4 class="section-label">启用平台</h4>
      <el-checkbox-group v-model="enabledPlatforms" class="platform-checks">
        <el-checkbox v-for="p in allPlatforms" :key="p" :label="p" :value="p" class="platform-check-item">
          <span class="platform-name">{{ p }}</span>
          <span class="platform-badge">{{ platformLabel(p) }}</span>
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <div class="platform-section">
      <h4 class="section-label">平台配置</h4>
      <el-table :data="platformConfigs" class="platform-table">
        <el-table-column prop="platform" label="平台" width="110">
          <template #default="{ row }">
            <span :class="['platform-tag', platformClass(row.platform)]">{{ row.platform }}</span>
          </template>
        </el-table-column>
        <el-table-column label="按钮文字" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.buttonText" size="small" placeholder="去完成" />
          </template>
        </el-table-column>
        <el-table-column label="流程描述" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.flowDesc" size="small" placeholder="点击按钮完成任务" />
          </template>
        </el-table-column>
        <el-table-column label="跳转 URI" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.jumpUri" size="small" placeholder="https://..." />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TaskPlatform } from '../../../api/platform'

const allPlatforms = ['WEB', 'ADMIN', 'IOS', 'ANDROID', 'MINIAPP']
const platformConfigs = ref<TaskPlatform[]>([])

const platformLabel = (p: string) =>
  ({ WEB: 'Web 端', ADMIN: '管理后台', IOS: 'iOS App', ANDROID: 'Android App', MINIAPP: '小程序' }[p] || p)

const platformClass = (p: string) =>
  ({ WEB: 'pf-web', ADMIN: 'pf-admin', IOS: 'pf-ios', ANDROID: 'pf-android', MINIAPP: 'pf-miniapp' }[p] || '')

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

<style scoped>
.platform-section {
  margin-bottom: 20px;
}
.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #4c1d95;
  margin: 0 0 8px;
}

.platform-checks {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.platform-check-item {
  margin-right: 0 !important;
}
.platform-name {
  font-weight: 600;
  font-size: 13px;
  margin-right: 4px;
}
.platform-badge {
  font-size: 10px;
  color: #a78bfa;
  font-weight: 400;
}

.platform-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}
.pf-web { background: #dbeafe; color: #1d4ed8; }
.pf-admin { background: #ede9fe; color: #6d28d9; }
.pf-ios { background: #f1f5f9; color: #374151; }
.pf-android { background: #dcfce7; color: #16a34a; }
.pf-miniapp { background: #fef3c7; color: #b45309; }

.platform-table {
  border-radius: 8px;
  overflow: hidden;
}
</style>
