<template>
  <div class="login-page">
    <div class="login-header">
      <div class="logo-icon">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <h1 class="app-name">营销任务平台</h1>
      <p class="app-desc">完成任务，赢取奖励</p>
    </div>

    <div class="form-section">
      <div class="form-title">账号登录</div>
      <van-form @submit="handleLogin">
        <van-cell-group inset>
          <van-field
            v-model="form.username"
            label="用户名"
            placeholder="请输入用户名"
            :rules="[{ required: true, message: '请输入用户名' }]"
          >
            <template #left-icon>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="2" stroke-linecap="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            </template>
          </van-field>
          <van-field
            v-model="form.password"
            type="password"
            label="密码"
            placeholder="请输入密码"
            :rules="[{ required: true, message: '请输入密码' }]"
          >
            <template #left-icon>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="2" stroke-linecap="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            </template>
          </van-field>
          <van-field
            v-model="form.captchaCode"
            label="验证码"
            placeholder="请输入验证码"
            :rules="[{ required: true, message: '请输入验证码' }]"
          >
            <template #left-icon>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="2" stroke-linecap="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </template>
            <template #extra>
              <div class="captcha-box" @click="refreshCaptcha">
                <img v-if="captchaImage" :src="captchaImage" class="captcha-img" />
                <span v-else class="captcha-loading">点击获取</span>
              </div>
            </template>
          </van-field>
        </van-cell-group>

        <div class="submit-wrap">
          <van-button round block type="primary" native-type="submit" :loading="loading" size="large">
            登 录
          </van-button>
        </div>
      </van-form>

      <div class="switch-row">
        <span class="switch-text">还没有账号？</span>
        <van-button size="small" type="primary" plain round to="/register">去注册</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
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
    showToast('获取验证码失败')
  }
}

const handleLogin = async () => {
  loading.value = true
  try {
    const res = await authApi.login(form.username, form.password, captchaKey.value, form.captchaCode)
    const { token, userId, username, nickname } = res.data.data
    userStore.setAuth(token, userId, username, nickname)
    router.push('/tasks')
  } catch (e: any) {
    showToast(e.response?.data?.message || '登录失败')
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
  background: linear-gradient(180deg, #f0f5ff 0%, #f7f8fa 40%);
}

.login-header {
  text-align: center;
  padding: 40px 24px 24px;
}
.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  border-radius: 16px;
  color: #fff;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.25);
}
.app-name {
  margin: 14px 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
}
.app-desc {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

.form-section {
  padding: 0 8px;
}
.form-title {
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  padding: 0 16px 12px;
}

.captcha-box {
  height: 38px;
  width: 100px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  transition: border-color 0.2s;
}
.captcha-box:active {
  border-color: #2563eb;
}
.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.captcha-loading {
  font-size: 11px;
  color: #94a3b8;
}

.submit-wrap {
  margin: 20px 16px 0;
}
.submit-wrap :deep(.van-button--large) {
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
}
.switch-text {
  font-size: 13px;
  color: #94a3b8;
}
</style>
