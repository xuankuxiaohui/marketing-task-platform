<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">{{ isNew ? '新建签到活动' : '编辑签到活动' }}</span>
          <p class="page-sub">配置签到规则、连续签到奖励和补签策略</p>
        </div>
        <div class="header-actions">
          <el-button @click="$router.push('/signin-configs')">返回列表</el-button>
          <el-button type="primary" @click="save" :loading="saving">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="120px" class="config-form">
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="活动名称" required>
            <el-input v-model="form.name" placeholder="如：每日签到" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="统计周期" required>
            <el-select v-model="form.periodType" placeholder="选择周期">
              <el-option label="按周（周一重置）" value="WEEKLY" />
              <el-option label="按月（月初重置）" value="MONTHLY" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="基础积分" required>
            <el-input-number v-model="form.basePoints" :min="1" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="活动说明">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="签到活动描述" />
      </el-form-item>

      <el-divider content-position="left">时间窗口</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="开始时间">
            <el-date-picker v-model="form.startTime" type="datetime" placeholder="不限" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="结束时间">
            <el-date-picker v-model="form.endTime" type="datetime" placeholder="不限" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="积分过期天数">
            <el-input-number v-model="form.pointExpireDays" :min="0" placeholder="不过期" controls-position="right" style="width:100%" />
            <div class="field-hint">0 或留空表示永不过期</div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">连续签到奖励</el-divider>
      <div class="streak-section">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="最大连续天数">
              <el-input-number v-model="streakConfig.maxStreak" :min="1" :max="365" controls-position="right" style="width:100%" />
              <div class="field-hint">{{ form.periodType === 'WEEKLY' ? '按周建议不超过 7' : '按月建议不超过 31' }}</div>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="tier-table-header">
          <span class="tier-label">奖励阶梯</span>
          <el-button size="small" type="primary" plain @click="addTier">添加阶梯</el-button>
        </div>
        <el-table :data="streakConfig.tiers" border size="small" class="tier-table">
          <el-table-column label="连续签到第 N 天" min-width="160">
            <template #default="{ row }">
              <el-input-number v-model="row.day" :min="1" :max="streakConfig.maxStreak" size="small" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="奖励积分" min-width="160">
            <template #default="{ row }">
              <el-input-number v-model="row.bonus" :min="1" size="small" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button size="small" type="danger" text @click="removeTier($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="streakConfig.tiers.length === 0" class="tier-empty">暂无阶梯奖励，签到仅获得基础积分</div>
      </div>

      <el-divider content-position="left">补签设置</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="允许补签">
            <el-switch v-model="form.catchUpEnabled" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="补签积分消耗">
            <el-input-number v-model="form.catchUpCost" :min="0" :disabled="!form.catchUpEnabled" controls-position="right" style="width:100%" />
            <div class="field-hint">0 表示免费补签</div>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="最大补签天数">
            <el-input-number v-model="form.catchUpMaxDays" :min="1" :max="30" :disabled="!form.catchUpEnabled" controls-position="right" style="width:100%" />
            <div class="field-hint">可回溯补签的最大天数</div>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'SignInConfigEdit' })
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createSignInConfig, getSignInConfig, updateSignInConfig, type StreakConfigObj, type StreakTier } from '../../api/signin'

const route = useRoute()
const router = useRouter()
const saving = ref(false)

const id = computed(() => route.params.id ? Number(route.params.id) : null)
const isNew = computed(() => id.value === null)

const form = reactive({
  name: '',
  periodType: 'MONTHLY',
  basePoints: 10,
  description: '',
  startTime: '' as string,
  endTime: '' as string,
  pointExpireDays: undefined as number | undefined,
  catchUpEnabled: false,
  catchUpCost: 0,
  catchUpMaxDays: 3,
})

const streakConfig = reactive<StreakConfigObj>({
  maxStreak: 30,
  tiers: [] as StreakTier[],
})

function addTier() {
  const lastDay = streakConfig.tiers.length > 0 ? streakConfig.tiers[streakConfig.tiers.length - 1].day : 0
  streakConfig.tiers.push({ day: lastDay + 3, bonus: 20 })
}

function removeTier(index: number) {
  streakConfig.tiers.splice(index, 1)
}

function buildStreakConfigJson(): string | undefined {
  if (streakConfig.tiers.length === 0) return undefined
  return JSON.stringify({ maxStreak: streakConfig.maxStreak, tiers: streakConfig.tiers })
}

function parseStreakConfig(json?: string) {
  if (!json) return
  try {
    const obj = JSON.parse(json)
    streakConfig.maxStreak = obj.maxStreak || 30
    streakConfig.tiers = (obj.tiers || []).map((t: any) => ({ day: t.day, bonus: t.bonus }))
  } catch {
    // ignore
  }
}

onMounted(async () => {
  if (!isNew.value) {
    try {
      const { data } = await getSignInConfig(id.value!)
      const config = data.data
      form.name = config.name
      form.periodType = config.periodType
      form.basePoints = config.basePoints
      form.description = config.description || ''
      form.startTime = config.startTime || ''
      form.endTime = config.endTime || ''
      form.pointExpireDays = config.pointExpireDays || undefined
      form.catchUpEnabled = config.catchUpEnabled
      form.catchUpCost = config.catchUpCost
      form.catchUpMaxDays = config.catchUpMaxDays || 3
      parseStreakConfig(config.streakConfig)
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || '加载配置失败')
    }
  }
})

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入活动名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      startTime: form.startTime || null,
      endTime: form.endTime || null,
      streakConfig: buildStreakConfigJson(),
    }
    if (isNew.value) {
      await createSignInConfig(payload as any)
    } else {
      await updateSignInConfig(id.value!, payload as any)
    }
    router.push('/signin-configs')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.page-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
.header-actions {
  display: flex;
  gap: 8px;
}
.config-form {
  max-width: 960px;
}

.streak-section {
  background: var(--color-surface-raised);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 8px;
}

.tier-table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.tier-label {
  font-weight: 600;
  font-size: 13px;
  color: var(--color-text-primary);
}
.tier-table {
  margin-bottom: 8px;
}
.tier-empty {
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
  padding: 16px 0;
}

.field-hint {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 4px;
}
</style>
