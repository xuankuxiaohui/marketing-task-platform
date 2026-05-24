import { http } from './http'

export const authApi = {
  getCaptcha: () => http.get('/captcha'),
  login: (username: string, password: string, captchaKey: string, captchaCode: string) =>
    http.post('/client/auth/login', { username, password, captchaKey, captchaCode }),
  register: (data: {
    username: string
    password: string
    captchaKey: string
    captchaCode: string
    nickname?: string
    province?: string
    role?: string
    tags?: string
    orgId?: string
    level?: number
  }) => http.post('/client/auth/register', data),
}
