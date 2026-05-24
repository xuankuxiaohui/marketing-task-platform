<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">{{ isNew ? '新建奖品' : '编辑奖品' }}</span>
          <p class="page-sub">配置奖品类型、库存和发放规则</p>
        </div>
        <div class="header-actions">
          <el-button @click="$router.push('/prizes')">返回列表</el-button>
          <el-button type="primary" @click="save" :loading="saving">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="120px" class="prize-form">
      <!-- Basic info -->
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="奖品类型" required>
            <el-select v-model="form.type" placeholder="选择奖品类型" @change="onTypeChange">
              <el-option label="积分" value="POINT" />
              <el-option label="优惠券" value="COUPON" />
              <el-option label="徽章" value="BADGE" />
              <el-option label="实物" value="PHYSICAL" />
              <el-option label="会员卡" value="MEMBERSHIP" />
              <el-option label="内部" value="INTERNAL" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="奖品名称" required>
            <el-input v-model="form.name" placeholder="如：100积分" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Handler Bean">
            <el-input v-model="form.handlerBean" placeholder="自动填充" disabled />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="奖品描述" />
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="图标 URL">
            <el-input v-model="form.iconUrl" placeholder="https://..." />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="领奖专区图">
            <el-input v-model="form.claimZoneImageUrl" placeholder="https://..." />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- Type-specific params -->
      <el-divider content-position="left">类型参数 (JSON)</el-divider>
      <el-form-item label="Params JSON">
        <el-input
          v-model="form.paramsJson"
          type="textarea"
          :rows="4"
          placeholder='PointParams: {"amount":100} / CouponParams: {"templateId":"xxx","amount":5,"expireDays":7} / BadgeParams: {"badgeId":"vip","name":"VIP徽章"}'
        />
        <div class="field-hint">
          <span v-if="form.type === 'POINT'">格式: { "amount": 数量 }</span>
          <span v-else-if="form.type === 'COUPON'">格式: { "templateId": "模板ID", "amount": 面额, "expireDays": 过期天数 }</span>
          <span v-else-if="form.type === 'BADGE'">格式: { "badgeId": "徽章ID", "name": "名称" }</span>
          <span v-else-if="form.type === 'PHYSICAL'">格式: { "skuId": "SKU", "name": "名称", "requireAddress": true }</span>
          <span v-else-if="form.type === 'MEMBERSHIP'">格式: { "level": 等级, "durationDays": 天数 }</span>
          <span v-else>JSON 参数配置</span>
        </div>
      </el-form-item>

      <!-- Inventory -->
      <el-divider content-position="left">库存设置</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="总库存">
            <el-input-number v-model="form.totalStock" :min="0" placeholder="不限制" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="月库存">
            <el-input-number v-model="form.monthlyStock" :min="0" placeholder="不限制" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="日库存">
            <el-input-number v-model="form.dailyStock" :min="0" placeholder="不限制" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- User limits -->
      <el-divider content-position="left">用户限制</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="总上限">
            <el-input-number v-model="form.userTotalLimit" :min="0" placeholder="不限制" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="月上限">
            <el-input-number v-model="form.userMonthlyLimit" :min="0" placeholder="不限制" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="日上限">
            <el-input-number v-model="form.userDailyLimit" :min="0" placeholder="不限制" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- Claim settings -->
      <el-divider content-position="left">领取设置</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="自动发放">
            <el-switch v-model="form.autoGrant" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="有效期类型">
            <el-select v-model="form.claimExpireType" placeholder="选择" clearable>
              <el-option label="天数" value="DAYS" />
              <el-option label="自然月" value="CALENDAR_MONTH" />
              <el-option label="固定日期" value="FIXED_DATE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="有效期值">
            <el-input v-model="form.claimExpireValue" placeholder="如: 7 (天数) / 2026-12-31 (固定日期)" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="最大重试次数">
            <el-input-number v-model="form.maxRetry" :min="0" :max="10" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="启用">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- Time window -->
      <el-divider content-position="left">时间窗口</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="开始时间">
            <el-date-picker v-model="form.startTime" type="datetime" placeholder="不限" format="YYYY-MM-DD HH:mm" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="结束时间">
            <el-date-picker v-model="form.endTime" type="datetime" placeholder="不限" format="YYYY-MM-DD HH:mm" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- Group -->
      <el-divider content-position="left">奖品组（可选）</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="组标识">
            <el-input v-model="form.groupKey" placeholder="同组奖品使用相同 key" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="组策略">
            <el-select v-model="form.groupStrategy" placeholder="选择" clearable>
              <el-option label="随机" value="RANDOM" />
              <el-option label="权重" value="WEIGHTED" />
              <el-option label="顺序" value="SEQUENTIAL" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="权重">
            <el-input-number v-model="form.groupWeight" :min="1" controls-position="right" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createPrize, getPrize, updatePrize, type Prize } from '../../api/prize'

const route = useRoute()
const router = useRouter()
const saving = ref(false)

const id = computed(() => route.params.id ? Number(route.params.id) : null)
const isNew = computed(() => id.value === null)

const form = reactive<Prize>({
  type: 'POINT',
  name: '',
  description: '',
  handlerBean: '',
  paramsJson: '',
  totalStock: undefined,
  monthlyStock: undefined,
  dailyStock: undefined,
  userTotalLimit: undefined,
  userMonthlyLimit: undefined,
  userDailyLimit: undefined,
  autoGrant: false,
  claimExpireType: 'DAYS',
  claimExpireValue: '7',
  maxRetry: 3,
  enabled: true,
  groupWeight: 1,
})

const handlerBeanMap: Record<string, string> = {
  POINT: 'pointPrizeHandler',
  COUPON: 'couponPrizeHandler',
  BADGE: 'badgePrizeHandler',
  PHYSICAL: 'internalPrizeHandler',
  MEMBERSHIP: 'internalPrizeHandler',
  INTERNAL: 'internalPrizeHandler',
}

function onTypeChange(type: string) {
  form.handlerBean = handlerBeanMap[type] || 'internalPrizeHandler'
}

onMounted(async () => {
  if (!isNew.value) {
    try {
      const { data } = await getPrize(id.value!)
      const prize = data.data
      Object.assign(form, prize)
      if (!form.handlerBean) {
        form.handlerBean = handlerBeanMap[form.type] || 'internalPrizeHandler'
      }
    } catch (e) {
      console.error('Failed to load prize:', e)
    }
  } else {
    form.handlerBean = handlerBeanMap[form.type]
  }
})

async function save() {
  if (!form.name.trim()) {
    return
  }
  saving.value = true
  try {
    if (isNew.value) {
      await createPrize({ ...form })
    } else {
      await updatePrize(id.value!, { ...form })
    }
    router.push('/prizes')
  } catch (e) {
    console.error('Failed to save prize:', e)
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
  color: #2d1b69;
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #a78bfa;
}
.header-actions {
  display: flex;
  gap: 8px;
}

.prize-form {
  max-width: 960px;
}

.field-hint {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}
.field-hint span {
  font-family: 'SF Mono', 'Fira Code', monospace;
  background: #f8fafc;
  padding: 2px 6px;
  border-radius: 3px;
}
</style>
