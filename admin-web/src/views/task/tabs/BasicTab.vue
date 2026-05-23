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
      <el-input v-model="model.mutexGroupKey" placeholder="同一互斥组的任务不能同时进行，留空表示不参与互斥" />
      <span class="form-hint">同一互斥组的任务不能同时进行，留空表示不参与互斥</span>
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
  </el-form>
</template>

<script setup lang="ts">
const model = defineModel<any>({ required: true })
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
