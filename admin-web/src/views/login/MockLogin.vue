<template>
  <div class="mock-login-wrapper">
    <el-card class="mock-login-card">
      <template #header>
        <div class="card-header">
          <span>Mock 用户上下文</span>
          <span class="card-sub">模拟 C 端用户身份，透传 X-User-* HTTP Headers</span>
        </div>
      </template>
      <div class="accent-bar"></div>
      <el-form label-width="100px" :model="user" class="mock-form">
        <el-form-item label="User ID">
          <el-input v-model="user.userId" placeholder="u_demo" />
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="user.province" placeholder="BJ / SH / GD" />
        </el-form-item>
        <el-form-item label="角色">
          <el-input v-model="user.role" placeholder="vip / normal" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="user.tags" placeholder="vip, active (逗号分隔)" />
        </el-form-item>
        <el-form-item label="组织">
          <el-input v-model="user.orgId" placeholder="org_001" />
        </el-form-item>
        <el-form-item label="等级">
          <el-input-number v-model="user.level" :min="0" :max="99" />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="user.platform" style="width:160px">
            <el-option label="ADMIN" value="ADMIN" />
            <el-option label="WEB" value="WEB" />
            <el-option label="IOS" value="IOS" />
            <el-option label="ANDROID" value="ANDROID" />
            <el-option label="MINIAPP" value="MINIAPP" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '../../stores/user'

const user = useUserStore()
</script>

<style scoped>
.mock-login-wrapper {
  max-width: 520px;
}
.mock-login-card {
  position: relative;
  overflow: hidden;
}
.card-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.card-header span:first-child {
  font-size: 16px;
  font-weight: 700;
  color: #2d1b69;
}
.card-sub {
  font-size: 12px;
  color: #a78bfa;
}
.accent-bar {
  height: 3px;
  background: linear-gradient(90deg, #667eea, #764ba2);
  margin: -1px 20px 16px;
  border-radius: 2px;
}
.mock-form {
  padding-top: 4px;
}
</style>
