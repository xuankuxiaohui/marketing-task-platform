<template>
  <el-card>
    <template #header>Mock 用户上下文</template>
    <el-form label-width="120px" :model="user">
      <el-form-item label="User ID"><el-input v-model="user.userId" /></el-form-item>
      <el-form-item label="省份"><el-input v-model="user.province" /></el-form-item>
      <el-form-item label="角色"><el-input v-model="user.role" /></el-form-item>
      <el-form-item label="标签"><el-input v-model="user.tags" /></el-form-item>
      <el-form-item label="组织"><el-input v-model="user.orgId" /></el-form-item>
      <el-form-item label="等级"><el-input-number v-model="user.level" /></el-form-item>
      <el-form-item label="平台">
        <el-select v-model="user.platform">
          <el-option label="ADMIN" value="ADMIN" />
          <el-option label="WEB" value="WEB" />
        </el-select>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { useUserStore } from '../../stores/user'

const user = useUserStore()
</script>
