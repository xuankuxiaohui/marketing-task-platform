import axios from 'axios'
import { useUserStore } from '../stores/user'

export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const user = useUserStore()
  config.headers.set('X-User-Id', user.userId)
  config.headers.set('X-User-Province', user.province)
  config.headers.set('X-User-Role', user.role)
  config.headers.set('X-User-Tags', user.tags)
  config.headers.set('X-User-Org-Id', user.orgId)
  config.headers.set('X-User-Level', String(user.level))
  config.headers.set('X-Platform', user.platform)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    if (error.response?.status === 401) {
      // 未授权，跳转 Mock 登录页
      window.location.href = '/login'
    } else if (error.response?.status >= 500) {
      console.error('Server error:', message)
    }
    return Promise.reject(error)
  }
)
