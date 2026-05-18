import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    userId: 'u_demo',
    province: 'BJ',
    role: 'vip',
    tags: 'vip,active',
    orgId: 'org_001',
    level: 5,
    platform: 'WEB',
  }),
})
