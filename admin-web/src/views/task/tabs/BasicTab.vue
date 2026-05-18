<template>
  <el-form label-width="100px" :model="model">
    <el-form-item label="任务编码"><el-input v-model="model.code" placeholder="daily_checkin" /></el-form-item>
    <el-form-item label="任务名称"><el-input v-model="model.name" placeholder="日签到" /></el-form-item>
    <el-form-item label="任务描述"><el-input v-model="model.description" type="textarea" /></el-form-item>
    <el-form-item label="周期">
      <el-select v-model="model.periodType">
        <el-option label="一次性" value="ONCE" />
        <el-option label="日任务" value="DAILY" />
        <el-option label="月任务" value="MONTHLY" />
        <el-option label="Cron" value="CRON" />
        <el-option label="特殊任务" value="SPECIAL" />
      </el-select>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
const model = defineModel<any>({ required: true })
</script>
