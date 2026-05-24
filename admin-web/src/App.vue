<template>
  <template v-if="userStore.isAuthenticated">
    <el-container class="layout">
      <el-aside class="sidebar" width="220px">
        <div class="brand">
          <div class="brand-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5"/>
              <path d="M2 12l10 5 10-5"/>
            </svg>
          </div>
          <div>
            <div class="brand-name">营销任务平台</div>
            <div class="brand-sub">运营后台</div>
          </div>
        </div>

        <el-menu
          router
          :default-active="$route.path"
          class="sidebar-menu"
        >
          <el-menu-item index="/dashboard">
            <template #title>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" class="nav-icon">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <line x1="3" y1="9" x2="21" y2="9"/>
                <line x1="9" y1="21" x2="9" y2="9"/>
              </svg>
              <span>运营仪表盘</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/tasks">
            <template #title>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" class="nav-icon">
                <rect x="3" y="3" width="7" height="7" rx="1"/>
                <rect x="14" y="3" width="7" height="7" rx="1"/>
                <rect x="3" y="14" width="7" height="7" rx="1"/>
                <rect x="14" y="14" width="7" height="7" rx="1"/>
              </svg>
              <span>任务管理</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/simulate">
            <template #title>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" class="nav-icon">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              <span>模拟测试</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/mutex-groups">
            <template #title>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" class="nav-icon">
                <circle cx="8" cy="8" r="5"/>
                <circle cx="16" cy="16" r="5"/>
                <line x1="10.5" y1="10.5" x2="13.5" y2="13.5"/>
              </svg>
              <span>互斥组管理</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/prizes">
            <template #title>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" class="nav-icon">
                <circle cx="12" cy="8" r="5"/>
                <path d="M3 21l3-7h12l3 7"/>
                <line x1="12" y1="3" x2="12" y2="6"/>
              </svg>
              <span>奖品管理</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/instances">
            <template #title>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" class="nav-icon">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              <span>实例查询</span>
            </template>
          </el-menu-item>
        </el-menu>

        <div class="sidebar-footer">
          <span class="env-badge">DEV</span>
        </div>
      </el-aside>

      <el-container>
        <el-header class="top-header">
          <div class="header-left">
            <span class="header-breadcrumb">
              <template v-for="(crumb, i) in breadcrumbs" :key="i">
                <span v-if="i > 0" class="bc-sep">/</span>
                <span :class="['bc-item', { 'bc-last': i === breadcrumbs.length - 1 }]">{{ crumb }}</span>
              </template>
            </span>
          </div>
          <div class="header-right">
            <span class="user-avatar">
              {{ (userStore.nickname || userStore.username || '?')[0] }}
            </span>
            <span class="user-name">{{ userStore.nickname || userStore.username }}</span>
            <el-button text size="small" class="logout-btn" @click="handleLogout">退出</el-button>
          </div>
        </el-header>
        <el-main class="main-content">
          <router-view v-slot="{ Component }">
            <transition name="page-fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </template>
  <router-view v-else />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from './stores/user'
import { useRouter, useRoute } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

const titleMap: Record<string, string> = {
  '/dashboard': '运营仪表盘',
  '/tasks': '任务管理',
  '/tasks/new': '新建任务',
  '/instances': '实例查询',
  '/mutex-groups': '互斥组管理',
  '/prizes': '奖品管理',
  '/simulate': '模拟测试',
}

const breadcrumbs = computed(() => {
  const path = route.path
  const parts: string[] = []
  if (path.startsWith('/tasks/') && path.endsWith('/metrics')) {
    parts.push('任务管理', '任务指标')
  } else if (path.startsWith('/tasks/') && path !== '/tasks' && path !== '/tasks/new') {
    parts.push('任务管理', '编辑任务')
  } else if (path.startsWith('/mutex-groups/') && path !== '/mutex-groups') {
    parts.push('互斥组管理', '互斥组详情')
  } else if (path.startsWith('/prizes/') && path !== '/prizes') {
    parts.push('奖品管理', path === '/prizes/new' ? '新建奖品' : '编辑奖品')
  } else {
    parts.push(titleMap[path] || '营销任务平台')
  }
  return parts
})

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

/* Sidebar */
.sidebar {
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
}

.brand {
  padding: 18px 16px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.brand-icon {
  width: 36px;
  height: 36px;
  background: rgba(37, 99, 235, 0.15);
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #60a5fa;
  flex-shrink: 0;
}
.brand-name {
  color: #f1f5f9;
  font-weight: 700;
  font-size: 13px;
  line-height: 1.3;
}
.brand-sub {
  color: #64748b;
  font-size: 11px;
  line-height: 1.3;
}

.sidebar-menu {
  flex: 1;
  border-right: none !important;
  padding: 8px 10px;
  background: transparent;
}
.sidebar-menu :deep(.el-menu-item) {
  border-radius: 6px;
  margin-bottom: 2px;
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  color: #94a3b8;
  padding-left: 12px !important;
  transition: all 0.15s;
}
.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.05) !important;
  color: #e2e8f0 !important;
}
.sidebar-menu :deep(.el-menu-item.is-active) {
  background: #2563eb !important;
  color: #fff !important;
  font-weight: 500;
}
.nav-icon {
  margin-right: 8px;
  flex-shrink: 0;
  opacity: 0.55;
}
.sidebar-menu :deep(.el-menu-item.is-active) .nav-icon {
  opacity: 1;
}

.sidebar-footer {
  padding: 10px 16px 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.env-badge {
  background: rgba(37, 99, 235, 0.15);
  color: #60a5fa;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.5px;
  border: 1px solid rgba(37, 99, 235, 0.2);
}

/* Top Header */
.top-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 0 24px;
  height: 50px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.bc-sep {
  color: #cbd5e1;
  font-size: 11px;
}
.bc-item {
  color: #94a3b8;
  font-weight: 500;
}
.bc-last {
  color: #1e293b;
  font-weight: 700;
  font-size: 14px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-name {
  font-size: 13px;
  color: #475569;
}
.logout-btn {
  color: #94a3b8;
  font-size: 12px;
  margin-left: 4px;
}
.logout-btn:hover {
  color: #ef4444;
}

/* Main Content */
.main-content {
  background: #f8fafc;
  min-height: calc(100vh - 50px);
  padding: 20px 24px;
}

/* Page transition */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
