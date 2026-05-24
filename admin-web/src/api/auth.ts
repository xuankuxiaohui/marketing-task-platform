import { http } from './http'

export const authApi = {
  getCaptcha: () => http.get('/captcha'),
  login: (username: string, password: string, captchaKey: string, captchaCode: string) =>
    http.post('/admin/auth/login', { username, password, captchaKey, captchaCode }),
}
