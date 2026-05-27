import { defineStore } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'
import { resolveTabTitle } from '../composables/useTabTitle'

export interface TabItem {
  fullPath: string
  title: string
  name: string
  path: string
  closable: boolean
}

const HOME_TAB_PATH = '/tasks'
const STORAGE_KEY = 'admin_tabs'

export const useTabStore = defineStore('tab', {
  state: () => ({
    tabs: [] as TabItem[],
    activeTabFullPath: '' as string,
  }),

  getters: {
    cachedComponentNames(state): string[] {
      return [...new Set(state.tabs.map((t) => t.name).filter(Boolean))]
    },
  },

  actions: {
    addTab(route: RouteLocationNormalized) {
      if (route.meta.noTab) return

      const existing = this.tabs.find((t) => t.fullPath === route.fullPath)
      if (existing) {
        existing.title = resolveTabTitle(route)
        this.activeTabFullPath = route.fullPath
        return
      }

      const name = (route.name as string) || ''
      this.tabs.push({
        fullPath: route.fullPath,
        title: resolveTabTitle(route),
        name,
        path: route.path,
        closable: !(route.path === HOME_TAB_PATH && this.tabs.length === 0),
      })
      this.activeTabFullPath = route.fullPath
      this._persist()
    },

    removeTab(fullPath: string): string | null {
      const idx = this.tabs.findIndex((t) => t.fullPath === fullPath)
      if (idx === -1) return null
      if (this.tabs.length <= 1) return null

      this.tabs.splice(idx, 1)

      let navigateTo: string | null = null
      if (this.activeTabFullPath === fullPath) {
        const newIdx = Math.min(idx, this.tabs.length - 1)
        navigateTo = this.tabs[newIdx].fullPath
        this.activeTabFullPath = navigateTo
      }

      this._persist()
      return navigateTo
    },

    setActive(fullPath: string) {
      this.activeTabFullPath = fullPath
    },

    closeOtherTabs(keepFullPath: string) {
      this.tabs = this.tabs.filter((t) => t.fullPath === keepFullPath || !t.closable)
      this.activeTabFullPath = keepFullPath
      this._persist()
    },

    closeRightTabs(fullPath: string) {
      const idx = this.tabs.findIndex((t) => t.fullPath === fullPath)
      if (idx === -1) return
      this.tabs = this.tabs.filter((t, i) => i <= idx || !t.closable)
      this._persist()
    },

    restore() {
      try {
        const raw = sessionStorage.getItem(STORAGE_KEY)
        if (raw) {
          const parsed = JSON.parse(raw) as { tabs: TabItem[]; active: string }
          this.tabs = parsed.tabs
          this.activeTabFullPath = parsed.active
        }
      } catch {
        // Ignore corrupt data
      }
    },

    clearAll() {
      this.tabs = []
      this.activeTabFullPath = ''
      sessionStorage.removeItem(STORAGE_KEY)
    },

    _persist() {
      sessionStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ tabs: this.tabs, active: this.activeTabFullPath }),
      )
    },
  },
})
