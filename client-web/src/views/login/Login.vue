<template>
  <div class="login-page">
    <!-- Animated background -->
    <div class="login-bg">
      <div class="bg-blob blob-1"></div>
      <div class="bg-blob blob-2"></div>
      <div class="bg-blob blob-3"></div>
      <div class="bg-noise"></div>
    </div>

    <!-- Header -->
    <div class="login-header animate-in">
      <div class="logo-wrap">
        <div class="logo-icon">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <div class="logo-glow"></div>
      </div>
      <h1 class="app-name">营销任务平台</h1>
      <p class="app-desc">完成任务，赢取奖励</p>
    </div>

    <!-- Form Card -->
    <div class="form-card animate-in animate-in-delay-2">
      <div class="form-card-header">
        <span class="form-welcome">欢迎回来</span>
        <span class="form-subtitle">登录你的账号</span>
      </div>

      <van-form @submit="handleLogin">
        <div class="input-group">
          <div class="input-wrap">
            <div class="input-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            </div>
            <input
              v-model="form.username"
              type="text"
              class="custom-input"
              placeholder="用户名"
            />
          </div>
          <div class="input-divider"></div>
          <div class="input-wrap">
            <div class="input-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            </div>
            <input
              v-model="form.password"
              type="password"
              class="custom-input"
              placeholder="密码"
            />
          </div>
          <div class="input-divider"></div>
          <div class="input-wrap captcha-wrap">
            <div class="input-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </div>
            <input
              v-model="form.captchaCode"
              type="text"
              class="custom-input captcha-input"
              placeholder="验证码"
            />
            <div class="captcha-box" @click="refreshCaptcha">
              <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="验证码" />
              <span v-else class="captcha-loading">点击获取</span>
            </div>
          </div>
        </div>

        <div class="submit-wrap">
          <button type="submit" class="submit-btn" :class="{ loading }">
            <span v-if="!loading">登 录</span>
            <span v-else class="btn-loading">
              <span class="spinner"></span>
            </span>
          </button>
        </div>
      </van-form>

      <div class="switch-row">
        <span class="switch-text">还没有账号？</span>
        <router-link to="/register" class="switch-link">立即注册</router-link>
      </div>
    </div>

    <!-- Decorative bottom -->
    <div class="login-footer animate-in animate-in-delay-4">
      <div class="footer-dots">
        <span></span><span></span><span></span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from '../../utils/toast'
import { authApi } from '../../api/auth'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const captchaKey = ref('')
const captchaImage = ref('')

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const refreshCaptcha = async () => {
  try {
    captchaImage.value = ''
    const res = await authApi.getCaptcha()
    captchaKey.value = res.data.data.captchaKey
    captchaImage.value = res.data.data.captchaImage
  } catch {
    showToast.fail('获取验证码失败')
  }
}

const handleLogin = async () => {
  if (!form.username || !form.password || !form.captchaCode) {
    showToast.fail('请填写完整信息')
    return
  }
  loading.value = true
  try {
    const res = await authApi.login(form.username, form.password, captchaKey.value, form.captchaCode)
    const { token, userId, username, nickname, province, role, tags, orgId, level, platform } = res.data.data
    userStore.setAuth(token, userId, username, nickname,
      { province, role, tags, orgId, level, platform })
    router.push('/tasks')
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '登录失败')
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (userStore.isAuthenticated) {
    router.push('/tasks')
  } else {
    refreshCaptcha()
  }
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #0f0f23;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 24px;
}

/* Animated background blobs */
.login-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 0;
}

.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
}

.blob-1 {
  width: 300px;
  height: 300px;
  background: #6366f1;
  top: -80px;
  right: -60px;
  animation: float 8s ease-in-out infinite;
}

.blob-2 {
  width: 250px;
  height: 250px;
  background: #ec4899;
  bottom: 20%;
  left: -80px;
  animation: float 10s ease-in-out infinite 2s;
}

.blob-3 {
  width: 200px;
  height: 200px;
  background: #06b6d4;
  top: 40%;
  right: -40px;
  animation: float 12s ease-in-out infinite 4s;
}

.bg-noise {
  position: absolute;
  inset: 0;
  background: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.03'/%3E%3C/svg%3E");
  background-size: 256px 256px;
}

/* Header */
.login-header {
  position: relative;
  z-index: 1;
  text-align: center;
  padding-top: 72px;
  padding-bottom: 32px;
}

.logo-wrap {
  position: relative;
  display: inline-block;
}

.logo-icon {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, #6366f1, #a78bfa);
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  position: relative;
  z-index: 1;
  box-shadow: 0 12px 40px rgba(99, 102, 241, 0.4);
}

.logo-glow {
  position: absolute;
  inset: -8px;
  background: linear-gradient(135deg, #6366f1, #a78bfa);
  border-radius: 28px;
  opacity: 0.3;
  filter: blur(16px);
  animation: glow-pulse 3s ease-in-out infinite;
}

.app-name {
  margin: 20px 0 6px;
  font-size: 28px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: -0.5px;
}

.app-desc {
  margin: 0;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.55);
  letter-spacing: 0.5px;
}

/* Form Card */
.form-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  padding: 28px 24px 24px;
}

.form-card-header {
  text-align: center;
  margin-bottom: 24px;
}

.form-welcome {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
}

.form-subtitle {
  display: block;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
  margin-top: 4px;
}

/* Custom Inputs */
.input-group {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
  transition: border-color 0.3s ease;
}

.input-group:focus-within {
  border-color: rgba(99, 102, 241, 0.5);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.input-wrap {
  display: flex;
  align-items: center;
  padding: 0 16px;
  height: 52px;
}

.input-icon {
  color: rgba(255, 255, 255, 0.35);
  margin-right: 12px;
  flex-shrink: 0;
  display: flex;
  transition: color 0.3s ease;
}

.input-wrap:focus-within .input-icon {
  color: #a78bfa;
}

.custom-input {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  color: #ffffff;
  font-size: 15px;
  font-family: var(--font-sans);
  width: 100%;
}

.custom-input::placeholder {
  color: rgba(255, 255, 255, 0.3);
}

.input-divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.06);
  margin: 0 16px;
}

.captcha-wrap {
  height: 64px;
}

.captcha-input {
  flex: 1;
}

.captcha-box {
  height: 40px;
  width: 110px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.04);
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.captcha-box:active {
  transform: scale(0.96);
  border-color: rgba(99, 102, 241, 0.4);
}

.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 9px;
}

.captcha-loading {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.35);
}

/* Submit Button */
.submit-wrap {
  margin-top: 20px;
}

.submit-btn {
  width: 100%;
  height: 52px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #a78bfa 100%);
  background-size: 200% 200%;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  font-family: var(--font-sans);
  letter-spacing: 4px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.35);
}

.submit-btn:active:not(.loading) {
  transform: scale(0.98);
}

.submit-btn:not(.loading):hover {
  background-position: 100% 0;
  box-shadow: 0 12px 40px rgba(99, 102, 241, 0.45);
}

.submit-btn.loading {
  opacity: 0.8;
  cursor: not-allowed;
}

.btn-loading {
  display: inline-flex;
}

.spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Switch row */
.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 20px;
}

.switch-text {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
}

.switch-link {
  font-size: 13px;
  color: #a78bfa;
  font-weight: 600;
  text-decoration: none;
}

.switch-link:active {
  opacity: 0.7;
}

/* Footer */
.login-footer {
  position: relative;
  z-index: 1;
  margin-top: auto;
  padding-bottom: 32px;
}

.footer-dots {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.footer-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
}

.footer-dots span:nth-child(2) {
  background: rgba(99, 102, 241, 0.5);
}
</style>
