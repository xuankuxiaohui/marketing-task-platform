<template>
  <div class="app-layout">
    <div class="layout-content" :class="{ 'has-tabbar': showTabbar }">
      <router-view v-slot="{ Component, route: _route }">
        <transition name="page-transition" mode="out-in">
          <component :is="Component" :key="_route.fullPath" />
        </transition>
      </router-view>
    </div>

    <div v-if="showTabbar" class="tabbar-wrapper">
      <div class="tabbar">
        <div
          v-for="tab in tabs"
          :key="tab.name"
          :class="['tabbar-item', { active: activeTab === tab.name }]"
          @click="onTabChange(tab.name)"
        >
          <div class="tabbar-icon-wrap">
            <div class="tabbar-icon-bg" v-if="activeTab === tab.name"></div>
            <svg class="tabbar-icon" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <template v-if="tab.name === 'tasks'">
                <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2" />
                <rect x="9" y="3" width="6" height="4" rx="1" />
                <path d="m9 14 2 2 4-4" />
              </template>
              <template v-else-if="tab.name === 'signin'">
                <rect x="3" y="4" width="18" height="18" rx="2" />
                <path d="M16 2v4M8 2v4M3 10h18" />
                <path d="m9 16 2 2 4-4" />
              </template>
              <template v-else>
                <path d="M20 12v10H4V12" />
                <path d="M2 7h20v5H2z" />
                <path d="M12 22V7" />
                <path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z" />
                <path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z" />
              </template>
            </svg>
          </div>
          <span class="tabbar-label">{{ tab.label }}</span>
          <div class="tabbar-active-dot" v-if="activeTab === tab.name"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const tabs = [
  { name: 'tasks', label: '任务' },
  { name: 'signin', label: '签到' },
  { name: 'prizes', label: '奖品' },
]

const showTabbar = computed(() => {
  const matched = route.matched[route.matched.length - 1]
  return !matched?.meta?.hideTabbar
})

const activeTab = computed(() => {
  if (route.path.startsWith('/prizes')) return 'prizes'
  if (route.path.startsWith('/signin')) return 'signin'
  return 'tasks'
})

function onTabChange(name: string) {
  if (activeTab.value === name) return
  const map: Record<string, string> = { tasks: '/tasks', signin: '/signin', prizes: '/prizes' }
  router.push(map[name] || '/tasks')
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: var(--color-bg);
}

.layout-content {
  min-height: 100vh;
}

.layout-content.has-tabbar {
  padding-bottom: 72px;
}

/* Page transition */
.page-transition-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.page-transition-leave-active {
  transition: all 0.2s ease;
}
.page-transition-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-transition-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* Tabbar */
.tabbar-wrapper {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  padding: 0 12px;
  padding-bottom: max(8px, env(safe-area-inset-bottom));
}

.tabbar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-radius: 24px;
  padding: 6px 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08), 0 0 1px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.5);
}

.tabbar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 16px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  -webkit-tap-highlight-color: transparent;
}

.tabbar-item:active {
  transform: scale(0.92);
}

.tabbar-icon-wrap {
  position: relative;
  width: 36px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tabbar-icon-bg {
  position: absolute;
  inset: -2px;
  background: var(--color-brand-gradient);
  border-radius: 12px;
  opacity: 0.15;
  animation: fadeInScale 0.3s ease;
}

.tabbar-icon {
  position: relative;
  z-index: 1;
  color: var(--color-text-muted);
  transition: all 0.3s ease;
}

.tabbar-item.active .tabbar-icon {
  color: var(--color-brand);
  transform: scale(1.1);
}

.tabbar-label {
  font-size: 10px;
  font-weight: 500;
  color: var(--color-text-muted);
  transition: all 0.3s ease;
  letter-spacing: 0.2px;
}

.tabbar-item.active .tabbar-label {
  color: var(--color-brand);
  font-weight: 700;
}

.tabbar-active-dot {
  position: absolute;
  bottom: 0;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-brand);
  animation: bounce-in 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
</style>
