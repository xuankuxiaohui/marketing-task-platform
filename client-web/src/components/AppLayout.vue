<template>
  <div class="app-layout">
    <div class="layout-content" :class="{ 'has-tabbar': showTabbar }">
      <router-view v-slot="{ Component, route: _route }">
        <transition name="slide-left" mode="out-in">
          <component :is="Component" :key="_route.fullPath" />
        </transition>
      </router-view>
    </div>

    <van-tabbar v-if="showTabbar" v-model="activeTab" :fixed="true" :safe-area-inset-bottom="true" @change="onTabChange">
      <van-tabbar-item name="tasks" icon="todo-list-o">
        任务
      </van-tabbar-item>
      <van-tabbar-item name="prizes" icon="gift-o">
        奖品
      </van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const showTabbar = computed(() => {
  const matched = route.matched[route.matched.length - 1]
  return !matched?.meta?.hideTabbar
})

const activeTab = computed(() => {
  if (route.path.startsWith('/prizes')) return 'prizes'
  return 'tasks'
})

function onTabChange(name: string) {
  router.push(name === 'prizes' ? '/prizes' : '/tasks')
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
  padding-bottom: 50px;
}

/* Slide-left transition */
.slide-left-enter-active,
.slide-left-leave-active {
  transition: all 0.25s ease;
}
.slide-left-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.slide-left-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>
