<template>
  <div class="login-wrapper">
    <div class="login-brand">
      <div class="brand-icon">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <h1 class="brand-name">营销任务平台</h1>
      <p class="brand-sub">运营管理后台</p>
    </div>

    <el-card class="login-card" shadow="always">
      <div class="card-title">账号登录</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
          >
            <template #prefix>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" style="color: var(--color-text-muted)"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
          >
            <template #prefix>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" style="color: var(--color-text-muted)"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="验证码"
              size="large"
              class="captcha-input"
            />
            <div class="captcha-img-wrap" @click="refreshCaptcha" title="点击刷新验证码">
              <img v-if="captchaImage" :src="captchaImage" class="captcha-img" />
              <span v-else class="captcha-loading-text">加载中...</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" size="large" class="login-btn">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <transition name="fade">
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
      </transition>
    </el-card>

    <div class="login-footer">
      <span class="env-tag">DEV</span>
      <span class="hint-text">默认账号 admin / admin123</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../../api/auth'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const errorMsg = ref('')
const captchaKey = ref('')
const captchaImage = ref('')
const formRef = ref()

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const refreshCaptcha = async () => {
  try {
    captchaImage.value = ''
    const res = await authApi.getCaptcha()
    captchaKey.value = res.data.data.captchaKey
    captchaImage.value = res.data.data.captchaImage
  } catch {
    errorMsg.value = '获取验证码失败'
  }
}

const handleLogin = async () => {
  errorMsg.value = ''
  loading.value = true
  try {
    const res = await authApi.login(form.username, form.password, captchaKey.value, form.captchaCode)
    const { token, userId, username, nickname } = res.data.data
    userStore.setAuth(token, userId, username, nickname)
    router.push('/tasks')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '登录失败'
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
.login-wrapper {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #1e293b 0%, #334155 40%, #1e293b 100%);
  padding: 24px;
}

.login-brand {
  text-align: center;
  margin-bottom: 28px;
}
.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  background: rgba(37, 99, 235, 0.15);
  border: 1px solid rgba(37, 99, 235, 0.3);
  border-radius: 14px;
  color: var(--color-brand-secondary);
  margin-bottom: 14px;
}
.brand-name {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-inverse);
  letter-spacing: 0.5px;
}
.brand-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.login-card {
  width: 400px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.login-card :deep(.el-card__body) {
  padding: 32px 36px 28px;
}

.card-title {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 24px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  align-items: stretch;
}
.captcha-input {
  flex: 1;
}
.captcha-img-wrap {
  flex-shrink: 0;
  width: 120px;
  height: 40px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface-raised);
  transition: border-color 0.2s;
}
.captcha-img-wrap:hover {
  border-color: var(--color-brand-primary);
}
.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.captcha-loading-text {
  color: var(--color-text-muted);
  font-size: 12px;
}

.login-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
  letter-spacing: 4px;
  border-radius: 8px;
}

.error-msg {
  color: var(--color-danger);
  font-size: 13px;
  text-align: center;
  padding: 8px 12px;
  background: var(--color-danger-subtle);
  border-radius: 6px;
  margin-top: -8px;
  margin-bottom: 8px;
}

.login-footer {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.env-tag {
  background: rgba(37, 99, 235, 0.2);
  color: var(--color-brand-secondary);
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.5px;
  border: 1px solid rgba(37, 99, 235, 0.25);
}
.hint-text {
  font-size: 12px;
  color: var(--color-text-muted);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
