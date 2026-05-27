<template>
  <div class="tab-bar">
    <div class="tab-list">
      <div
        v-for="tab in tabStore.tabs"
        :key="tab.fullPath"
        :class="['tab-item', { active: tab.fullPath === tabStore.activeTabFullPath }]"
        @click="handleClickTab(tab)"
        @contextmenu.prevent="handleContextMenu($event, tab)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <span
          v-if="tab.closable"
          class="tab-close"
          @click.stop="handleCloseTab(tab)"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </span>
      </div>
    </div>
  </div>

  <teleport to="body">
    <div
      v-if="contextMenu.visible"
      class="tab-context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
    >
      <div class="ctx-item" @click="handleCloseCurrent">关闭当前</div>
      <div class="ctx-item" @click="handleCloseOther">关闭其他</div>
      <div class="ctx-item" @click="handleCloseRight">关闭右侧</div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTabStore, type TabItem } from '../stores/tab'

defineOptions({ name: 'TabBar' })

const router = useRouter()
const tabStore = useTabStore()

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  tab: null as TabItem | null,
})

function handleClickTab(tab: TabItem) {
  if (tab.fullPath !== tabStore.activeTabFullPath) {
    router.push(tab.fullPath)
  }
}

function handleCloseTab(tab: TabItem) {
  const navigateTo = tabStore.removeTab(tab.fullPath)
  if (navigateTo) {
    router.push(navigateTo)
  }
}

function handleContextMenu(event: MouseEvent, tab: TabItem) {
  contextMenu.visible = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.tab = tab
}

function closeContextMenu() {
  contextMenu.visible = false
}

function handleCloseCurrent() {
  if (contextMenu.tab?.closable) {
    handleCloseTab(contextMenu.tab)
  }
  closeContextMenu()
}

function handleCloseOther() {
  if (contextMenu.tab) {
    tabStore.closeOtherTabs(contextMenu.tab.fullPath)
    if (contextMenu.tab.fullPath !== tabStore.activeTabFullPath) {
      router.push(contextMenu.tab.fullPath)
    }
  }
  closeContextMenu()
}

function handleCloseRight() {
  if (contextMenu.tab) {
    tabStore.closeRightTabs(contextMenu.tab.fullPath)
  }
  closeContextMenu()
}

onMounted(() => document.addEventListener('click', closeContextMenu))
onUnmounted(() => document.removeEventListener('click', closeContextMenu))
</script>

<style scoped>
.tab-bar {
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  height: 36px;
  display: flex;
  align-items: center;
  padding: 0 var(--space-3);
  overflow-x: auto;
  white-space: nowrap;
  flex-shrink: 0;
}

.tab-list {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  height: 28px;
  font-size: 12px;
  color: var(--color-text-muted);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  user-select: none;
}

.tab-item:hover {
  color: var(--color-text-secondary);
  border-color: var(--color-text-disabled);
}

.tab-item.active {
  color: var(--color-brand-primary);
  background: var(--color-brand-primary-subtle);
  border-color: var(--color-brand-primary);
  font-weight: 500;
}

.tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  color: var(--color-text-disabled);
  transition: all var(--transition-fast);
}

.tab-close:hover {
  background: var(--color-danger-subtle);
  color: var(--color-danger);
}
</style>

<style>
.tab-context-menu {
  position: fixed;
  z-index: 3000;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 4px 0;
  min-width: 120px;
}

.ctx-item {
  padding: 6px 16px;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.ctx-item:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}
</style>
