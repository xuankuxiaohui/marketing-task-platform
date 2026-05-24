<template>
  <el-form label-width="100px" :model="model" class="basic-form">
    <el-form-item label="任务编码">
      <el-input v-model="model.code" placeholder="例如：daily_checkin" />
    </el-form-item>
    <el-form-item label="任务名称">
      <el-input v-model="model.name" placeholder="例如：每日签到" />
    </el-form-item>
    <el-form-item label="任务描述">
      <el-input v-model="model.description" type="textarea" :rows="3" placeholder="描述任务内容和完成条件" />
    </el-form-item>
    <el-form-item label="互斥组">
      <el-select v-model="model.mutexGroupId" placeholder="选择互斥组（可不选）" clearable style="width:100%">
        <el-option v-for="g in mutexGroups" :key="g.id" :label="g.name" :value="g.id" />
      </el-select>
      <span class="form-hint">
        同一互斥组的任务不能同时进行，留空表示不参与互斥。
        <el-link type="primary" @click="$router.push('/mutex-groups')" style="font-size:11px">管理互斥组</el-link>
      </span>
    </el-form-item>
    <el-form-item label="周期类型">
      <el-select v-model="model.periodType" style="width:200px">
        <el-option label="一次性" value="ONCE" />
        <el-option label="每日" value="DAILY" />
        <el-option label="每月" value="MONTHLY" />
        <el-option label="Cron 表达式" value="CRON" />
        <el-option label="特殊日期" value="SPECIAL" />
      </el-select>
      <span class="form-hint">
        <template v-if="model.periodType === 'ONCE'">任务仅可完成一次</template>
        <template v-else-if="model.periodType === 'DAILY'">每天重置，cycle_key = 日期 yyyyMMdd</template>
        <template v-else-if="model.periodType === 'MONTHLY'">每月重置，cycle_key = yyyyMM</template>
        <template v-else-if="model.periodType === 'CRON'">按 Cron 表达式触发，cycle_key = fire time yyyyMMddHHmm</template>
        <template v-else-if="model.periodType === 'SPECIAL'">特殊活动周期，需指定 cycle_key</template>
      </span>
    </el-form-item>
    <el-form-item label="灰度类型">
      <el-select v-model="model.grayType" style="width:200px" @change="onGrayTypeChange">
        <el-option label="无灰度" value="NONE" />
        <el-option label="百分比" value="PERCENTAGE" />
        <el-option label="AB 实验" value="AB" />
        <el-option label="人群包" value="CROWD" />
      </el-select>
      <span class="form-hint">控制任务对用户的可见范围，NONE 表示全员可见</span>
    </el-form-item>
    <el-form-item v-if="model.grayType === 'PERCENTAGE'" label="灰度比例">
      <div style="display:flex;align-items:center;gap:12px;width:100%">
        <el-slider v-model="grayPercent" :max="100" style="flex:1" show-input />
      </div>
      <span class="form-hint">只有 hash(userId + taskId) % 100 &lt; 比例的可见</span>
    </el-form-item>
    <el-form-item v-if="model.grayType === 'AB'" label="AB 分组">
      <div style="width:100%">
        <div v-for="(g, i) in abGroups" :key="i" style="display:flex;gap:8px;margin-bottom:6px;align-items:center">
          <el-input v-model="g.name" placeholder="组名" style="width:120px" size="small" />
          <el-input-number v-model="g.percent" :min="0" :max="100" size="small" style="width:100px" />
          <span style="font-size:12px;color:#909399">%</span>
          <el-button size="small" text type="danger" @click="abGroups.splice(i,1)" :disabled="abGroups.length<=2">×</el-button>
        </div>
        <el-button size="small" text type="primary" @click="abGroups.push({name:'',percent:0})">+ 添加分组</el-button>
      </div>
      <span class="form-hint">按累计百分比分配，表达式: inABGroup('组名')</span>
    </el-form-item>
    <el-form-item v-if="model.grayType === 'CROWD'" label="人群包 ID">
      <el-input v-model="crowdIdsText" placeholder="逗号分隔，例如: 1,2,3" />
      <span class="form-hint">用户必须在任一人群包中才可见，表达式: inCrowd(人群ID)</span>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { onMounted, ref, watch, computed } from 'vue'
import { listMutexGroups, type MutexGroup } from '../../../api/mutex-group'

const model = defineModel<any>({ required: true })

const mutexGroups = ref<MutexGroup[]>([])

const abGroups = ref<{ name: string; percent: number }[]>([])
const grayPercent = ref(0)
const crowdIdsText = ref('')

function parseGrayConfig() {
  if (!model.value.grayConfig) return
  try {
    const cfg = JSON.parse(model.value.grayConfig)
    if (model.value.grayType === 'PERCENTAGE') {
      grayPercent.value = cfg.percent || 0
    } else if (model.value.grayType === 'AB') {
      abGroups.value = cfg.groups || []
    } else if (model.value.grayType === 'CROWD') {
      crowdIdsText.value = (cfg.crowdIds || []).join(',')
    }
  } catch {}
}

function syncGrayConfig() {
  if (model.value.grayType === 'PERCENTAGE') {
    model.value.grayConfig = JSON.stringify({ percent: grayPercent.value })
  } else if (model.value.grayType === 'AB') {
    model.value.grayConfig = JSON.stringify({ groups: abGroups.value })
  } else if (model.value.grayType === 'CROWD') {
    const ids = crowdIdsText.value.split(',').map(s => parseInt(s.trim())).filter(n => !isNaN(n))
    model.value.grayConfig = JSON.stringify({ crowdIds: ids })
  } else {
    model.value.grayConfig = null
  }
}

function onGrayTypeChange() {
  grayPercent.value = 50
  abGroups.value = [{ name: 'A', percent: 50 }, { name: 'B', percent: 50 }]
  crowdIdsText.value = ''
  syncGrayConfig()
}

watch([grayPercent, abGroups, crowdIdsText], () => syncGrayConfig(), { deep: true })

onMounted(async () => {
  try {
    const { data } = await listMutexGroups()
    mutexGroups.value = data.data
  } catch {}
  if (model.value.grayType && model.value.grayType !== 'NONE') {
    parseGrayConfig()
  } else if (!model.value.grayType) {
    model.value.grayType = 'NONE'
  }
})
</script>

<style scoped>
.basic-form {
  max-width: 560px;
  padding-top: 8px;
}
.form-hint {
  display: block;
  font-size: 11px;
  color: #a78bfa;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
