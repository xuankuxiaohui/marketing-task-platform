import axios from 'axios'
import { useUserStore } from '../stores/user'

export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const user = useUserStore()
  if (user.userId) config.headers.set('X-User-Id', user.userId)
  if (user.province) config.headers.set('X-User-Province', user.province)
  if (user.role) config.headers.set('X-User-Role', user.role)
  if (user.tags) config.headers.set('X-User-Tags', user.tags)
  if (user.orgId) config.headers.set('X-User-Org-Id', user.orgId)
  config.headers.set('X-User-Level', String(user.level ?? ''))
  config.headers.set('X-Platform', user.platform || 'WEB')
  return config
})


http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    if (error.response?.status === 401) {
      window.location.href = '/login'
    } else if (error.response?.status >= 500) {
      console.error('Server error:', message)
    }
    return Promise.reject(error)
  }
)
