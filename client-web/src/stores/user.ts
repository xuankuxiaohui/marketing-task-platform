import { defineStore } from 'pinia'

const LS_PREFIX = 'client_'

export interface ProfileData {
  province?: string
  role?: string
  tags?: string
  orgId?: string
  level?: number
  platform?: string
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: (localStorage.getItem(LS_PREFIX + 'token') || '') as string,
    userId: (localStorage.getItem(LS_PREFIX + 'userId') || '') as string,
    username: (localStorage.getItem(LS_PREFIX + 'username') || '') as string,
    nickname: (localStorage.getItem(LS_PREFIX + 'nickname') || '') as string,
    province: (localStorage.getItem(LS_PREFIX + 'province') || '') as string,
    role: (localStorage.getItem(LS_PREFIX + 'role') || '') as string,
    tags: (localStorage.getItem(LS_PREFIX + 'tags') || '') as string,
    orgId: (localStorage.getItem(LS_PREFIX + 'orgId') || '') as string,
    level: Number(localStorage.getItem(LS_PREFIX + 'level') || '0'),
    platform: (localStorage.getItem(LS_PREFIX + 'platform') || 'WEB') as string,
  }),
  getters: {
    isAuthenticated: (state): boolean => !!state.token,
  },
  actions: {
    setAuth(token: string, userId: string, username: string, nickname: string, profile?: ProfileData) {
      this.token = token
      this.userId = userId
      this.username = username
      this.nickname = nickname
      localStorage.setItem(LS_PREFIX + 'token', token)
      localStorage.setItem(LS_PREFIX + 'userId', userId)
      localStorage.setItem(LS_PREFIX + 'username', username)
      localStorage.setItem(LS_PREFIX + 'nickname', nickname)

      // Profile data comes from the login response (no longer decoded from JWT)
      if (profile) {
        this.province = profile.province || ''
        this.role = profile.role || ''
        this.tags = profile.tags || ''
        this.orgId = profile.orgId || ''
        this.level = profile.level ?? 0
        this.platform = profile.platform || 'WEB'
        localStorage.setItem(LS_PREFIX + 'province', this.province)
        localStorage.setItem(LS_PREFIX + 'role', this.role)
        localStorage.setItem(LS_PREFIX + 'tags', this.tags)
        localStorage.setItem(LS_PREFIX + 'orgId', this.orgId)
        localStorage.setItem(LS_PREFIX + 'level', String(this.level))
        localStorage.setItem(LS_PREFIX + 'platform', this.platform)
      }
    },
    logout() {
      this.token = ''
      this.userId = ''
      this.username = ''
      this.nickname = ''
      this.province = ''
      this.role = ''
      this.tags = ''
      this.orgId = ''
      this.level = 0
      this.platform = 'WEB'
      const keys = ['token', 'userId', 'username', 'nickname', 'province', 'role', 'tags', 'orgId', 'level', 'platform']
      keys.forEach((k) => localStorage.removeItem(LS_PREFIX + k))
    },
  },
})
