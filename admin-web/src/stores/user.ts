import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    userId: 'admin_mock',
    province: 'BJ',
    role: 'admin',
    tags: 'admin,operator',
    orgId: 'org_001',
    level: 5,
    platform: 'ADMIN',
  }),
})
