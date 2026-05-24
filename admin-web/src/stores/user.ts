import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: (localStorage.getItem('admin_token') || '') as string,
    userId: (localStorage.getItem('admin_userId') || '') as string,
    username: (localStorage.getItem('admin_username') || '') as string,
    nickname: (localStorage.getItem('admin_nickname') || '') as string,
    // Mock fallback fields
    province: 'BJ',
    role: 'admin',
    tags: 'admin,operator',
    orgId: 'org_001',
    level: 5,
    platform: 'ADMIN',
  }),
  getters: {
    isAuthenticated: (state): boolean => !!state.token,
  },
  actions: {
    setAuth(token: string, userId: string, username: string, nickname: string) {
      this.token = token
      this.userId = userId
      this.username = username
      this.nickname = nickname
      localStorage.setItem('admin_token', token)
      localStorage.setItem('admin_userId', userId)
      localStorage.setItem('admin_username', username)
      localStorage.setItem('admin_nickname', nickname)
    },
    logout() {
      this.token = ''
      this.userId = ''
      this.username = ''
      this.nickname = ''
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_userId')
      localStorage.removeItem('admin_username')
      localStorage.removeItem('admin_nickname')
    },
  },
})
