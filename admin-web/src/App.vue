<template>
  <el-container class="layout">
    <el-aside class="sidebar" width="220px">
      <div class="brand">
        <span class="brand-dot"></span>
        <span>营销任务平台</span>
      </div>
      <el-menu
        router
        :default-active="$route.path"
        class="sidebar-menu"
        background-color="transparent"
        text-color="#94a3b8"
        active-text-color="#fff"
      >
        <el-menu-item index="/tasks">
          <span class="nav-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
          </span>
          <span>任务管理</span>
        </el-menu-item>
        <el-menu-item index="/instances">
          <span class="nav-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          </span>
          <span>实例查询</span>
        </el-menu-item>
        <el-menu-item index="/login">
          <span class="nav-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          </span>
          <span>Mock 用户</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="top-header">
        <div class="header-left">
          <span class="header-title">运营后台</span>
        </div>
        <div class="header-right">
          <span class="env-badge">DEV</span>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.sidebar {
  background: #1e293b;
}

.brand {
  padding: 18px 20px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.brand-dot {
  width: 20px;
  height: 20px;
  background: #2563eb;
  border-radius: 5px;
  flex-shrink: 0;
}

.nav-icon {
  display: inline-flex;
  align-items: center;
  margin-right: 8px;
  opacity: 0.6;
}
.sidebar-menu {
  border-right: none !important;
  padding: 8px 10px;
}
.sidebar-menu .el-menu-item {
  border-radius: 6px;
  margin-bottom: 1px;
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  padding-left: 12px !important;
  transition: all 0.15s;
}
.sidebar-menu .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.06) !important;
  color: #e2e8f0 !important;
}
.sidebar-menu .el-menu-item.is-active {
  background: #2563eb !important;
  color: #fff !important;
  font-weight: 500;
}
.sidebar-menu .el-menu-item.is-active .nav-icon {
  opacity: 1;
}

.top-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 0 24px;
  height: 50px;
}
.header-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.env-badge {
  background: #f1f5f9;
  color: #64748b;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.5px;
  border: 1px solid #e2e8f0;
}

.main-content {
  background: #f8fafc;
  min-height: calc(100vh - 50px);
  padding: 20px 24px;
}
</style>
