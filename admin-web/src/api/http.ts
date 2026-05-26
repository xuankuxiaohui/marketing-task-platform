import axios from 'axios'
import { useUserStore } from '../stores/user'

export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const user = useUserStore()
  if (user.token) {
    config.headers.set('Authorization', `Bearer ${user.token}`)
  } else {
    // Fallback: mock headers (dev mode)
    config.headers.set('X-User-Id', user.userId || '')
    config.headers.set('X-User-Province', user.province)
    config.headers.set('X-User-Role', user.role)
    config.headers.set('X-User-Tags', user.tags)
    config.headers.set('X-User-Org-Id', user.orgId)
    config.headers.set('X-User-Level', String(user.level))
    config.headers.set('X-Platform', user.platform)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    if (error.response?.status === 401) {
      const user = useUserStore()
      if (user.token) {
        user.logout()
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  }
)
