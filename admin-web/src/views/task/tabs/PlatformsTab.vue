<template>
  <div>
    <!-- Task-level platform config -->
    <div class="platform-section">
      <h4 class="section-label">任务级端配置</h4>
      <el-checkbox-group v-model="enabledPlatforms" class="platform-checks">
        <el-checkbox v-for="p in allPlatforms" :key="p" :label="p" :value="p" class="platform-check-item">
          <span class="platform-name">{{ p }}</span>
          <span class="platform-badge">{{ platformLabel(p) }}</span>
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <div class="platform-section" v-if="platformConfigs.length">
      <el-table :data="platformConfigs" class="platform-table">
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <span :class="['platform-tag', platformClass(row.platform)]">{{ row.platform }}</span>
          </template>
        </el-table-column>
        <el-table-column label="按钮文字" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.buttonText" size="small" placeholder="去完成" />
          </template>
        </el-table-column>
        <el-table-column label="流程描述" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.flowDesc" size="small" placeholder="点击按钮完成任务" />
          </template>
        </el-table-column>
        <el-table-column label="跳转 URI" min-width="170">
          <template #default="{ row }">
            <el-input v-model="row.jumpUri" size="small" placeholder="https://..." />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Step-level platform config -->
    <div class="platform-section" v-if="steps.length">
      <h4 class="section-label">步骤级端配置</h4>
      <p class="section-hint">配置每个步骤在各平台的按钮、操作和跳转。留空则继承任务级默认值。</p>

      <div class="step-platform-list">
        <div v-for="step in steps" :key="step.code || step.seq" class="step-platform-card">
          <div class="sp-card-title">
            <span class="sp-step-seq">{{ step.seq }}</span>
            <code class="sp-step-code">{{ step.code }}</code>
            <span class="sp-step-name">{{ step.name || '未命名' }}</span>
            <span :class="['type-tag-mini', typeClass(step.type)]">{{ step.type }}</span>
          </div>
          <div class="sp-platforms-row">
            <div v-for="p in enabledPlatforms" :key="p" class="sp-platform-item">
              <span :class="['pf-tag-mini', platformClass(p)]">{{ p }}</span>
              <el-input
                :model-value="getStepPlatformConfig(step, p).buttonText"
                @update:model-value="v => setStepPlatformConfig(step, p, 'buttonText', v)"
                size="small" placeholder="按钮文案" style="width:110px"
              />
              <el-select
                :model-value="getStepPlatformConfig(step, p).actionType || 'NONE'"
                @update:model-value="v => setStepPlatformConfig(step, p, 'actionType', v)"
                size="small" style="width:130px"
              >
                <el-option label="无操作" value="NONE" />
                <el-option label="手动领奖" value="CLAIM_REWARD" />
                <el-option label="打开链接" value="OPEN_URL" />
                <el-option label="唤起原生" value="NATIVE_SCHEME" />
                <el-option label="小程序路径" value="MINIAPP_PATH" />
                <el-option label="分享" value="SHARE" />
              </el-select>
              <el-select
                :model-value="getStepPlatformConfig(step, p).jumpType || 'NONE'"
                @update:model-value="v => setStepPlatformConfig(step, p, 'jumpType', v as string)"
                size="small" style="width:110px"
              >
                <el-option label="无跳转" value="NONE" />
                <el-option label="URL" value="URL" />
                <el-option label="原生 Scheme" value="NATIVE_SCHEME" />
                <el-option label="小程序路径" value="MINIAPP_PATH" />
                <el-option label="API 调用" value="API_CALL" />
              </el-select>
              <el-input
                v-if="getStepPlatformConfig(step, p).jumpType && getStepPlatformConfig(step, p).jumpType !== 'NONE'"
                :model-value="getStepPlatformConfig(step, p).jumpTarget"
                @update:model-value="v => setStepPlatformConfig(step, p, 'jumpTarget', v)"
                size="small" placeholder="跳转目标" style="width:140px"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TaskPlatform } from '../../../api/platform'
import type { StepPlatformConfig } from '../../../api/step-platform'
import type { Step } from '../../../api/step'

const allPlatforms = ['WEB', 'IOS', 'ANDROID', 'MINIAPP']
const platformConfigs = ref<TaskPlatform[]>([])
const stepPlatformConfigs = ref<StepPlatformConfig[]>([])

const steps = ref<Step[]>([])

const platformLabel = (p: string) =>
  ({ WEB: 'Web 端', IOS: 'iOS App', ANDROID: 'Android App', MINIAPP: '小程序' }[p] || p)

const platformClass = (p: string) =>
  ({ WEB: 'pf-web', IOS: 'pf-ios', ANDROID: 'pf-android', MINIAPP: 'pf-miniapp' }[p] || '')

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

function typeClass(t: string) {
  return ({ CLICK: 'tg-click', CALLBACK: 'tg-callback', PROGRESS: 'tg-progress', REWARD: 'tg-reward', PASSIVE: 'tg-passive' })[t] || 'tg-click'
}

function getStepPlatformConfig(step: Step, platform: string): StepPlatformConfig {
  const code = step.code || `__seq_${step.seq}`
  let cfg = stepPlatformConfigs.value.find(c => c.stepCode === code && c.platform === platform)
  if (!cfg) {
    cfg = { stepCode: code, platform, jumpType: 'NONE', actionType: 'NONE' }
  }
  return cfg
}

function setStepPlatformConfig(step: Step, platform: string, field: string, value: any) {
  const code = step.code || `__seq_${step.seq}`
  let cfg = stepPlatformConfigs.value.find(c => c.stepCode === code && c.platform === platform)
  if (!cfg) {
    cfg = { stepCode: code, platform, jumpType: 'NONE', actionType: 'NONE' }
    stepPlatformConfigs.value.push(cfg)
  }
  ;(cfg as any)[field] = value || undefined
}

function setPlatforms(data: TaskPlatform[]) {
  platformConfigs.value = data || []
}

function setSteps(data: Step[]) {
  steps.value = data || []
}

function setStepPlatforms(data: StepPlatformConfig[]) {
  stepPlatformConfigs.value = data || []
}

defineExpose({
  getPlatforms: () => platformConfigs.value,
  setPlatforms,
  setSteps,
  setStepPlatforms,
  getStepPlatforms: () => stepPlatformConfigs.value,
})
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
.section-hint {
  font-size: 11px;
  color: #a78bfa;
  margin: -4px 0 10px;
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
.pf-ios { background: #f1f5f9; color: #374151; }
.pf-android { background: #dcfce7; color: #16a34a; }
.pf-miniapp { background: #fef3c7; color: #b45309; }

.platform-table {
  border-radius: 8px;
  overflow: hidden;
}

/* Step-level config */
.step-platform-card {
  background: #fafbff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 8px;
}
.sp-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.sp-step-seq {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 50%;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}
.sp-step-code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 11px;
  background: #f5f3ff;
  color: #6d28d9;
  padding: 1px 6px;
  border-radius: 3px;
}
.sp-step-name {
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
}
.type-tag-mini {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 9px;
  font-weight: 700;
  text-transform: uppercase;
}
.tg-click { background: #dbeafe; color: #1d4ed8; }
.tg-callback { background: #fef3c7; color: #b45309; }
.tg-progress { background: #ede9fe; color: #6d28d9; }
.tg-reward { background: #d1fae5; color: #047857; }
.tg-passive { background: #f1f5f9; color: #64748b; }

.sp-platforms-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.sp-platform-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pf-tag-mini {
  display: inline-block;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 600;
  width: 55px;
  text-align: center;
  flex-shrink: 0;
}
</style>
