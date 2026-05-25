<template>
  <div class="register-page">
    <van-nav-bar title="注册账号" left-arrow @click-left="$router.back()" />

    <div class="register-header">
      <p class="register-welcome">加入营销任务平台</p>
      <p class="register-hint">完成注册即可参与任务赢取奖励</p>
    </div>

    <div class="form-section">
      <div class="section-label">登录信息</div>
      <van-form @submit="handleRegister">
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
            placeholder="至少6位密码"
            :rules="[{ required: true, message: '请输入密码' }, { validator: (v: string) => v.length >= 6, message: '密码至少6位' }]"
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

        <div class="section-label profile-label">用户画像<span class="label-optional">（可选，用于任务匹配）</span></div>
        <van-cell-group inset>
          <van-field v-model="form.nickname" label="昵称" placeholder="选填" />
          <van-field v-model="form.province" label="省份" placeholder="如 BJ、SH" />
          <van-field v-model="form.role" label="角色" placeholder="如 vip、normal" />
          <van-field v-model="form.tags" label="标签" placeholder="逗号分隔，如 vip,active" />
          <van-field v-model="form.orgId" label="组织" placeholder="如 org_001" />
          <van-field v-model="form.level" label="等级" type="digit" placeholder="数字" />
        </van-cell-group>

        <div class="submit-wrap">
          <van-button round block type="primary" native-type="submit" :loading="loading" size="large">
            注 册
          </van-button>
        </div>
      </van-form>

      <div class="switch-row">
        <span class="switch-text">已有账号？</span>
        <van-button size="small" plain round to="/login">去登录</van-button>
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
  nickname: '',
  province: '',
  role: '',
  tags: '',
  orgId: '',
  level: '' as string,
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

const handleRegister = async () => {
  loading.value = true
  try {
    const res = await authApi.register({
      username: form.username,
      password: form.password,
      captchaKey: captchaKey.value,
      captchaCode: form.captchaCode,
      nickname: form.nickname || undefined,
      province: form.province || undefined,
      role: form.role || undefined,
      tags: form.tags || undefined,
      orgId: form.orgId || undefined,
      level: form.level ? Number(form.level) : undefined,
    })
    const { token, userId, username, nickname } = res.data.data
    userStore.setAuth(token, userId, username, nickname)
    showToast('注册成功')
    router.push('/tasks')
  } catch (e: any) {
    showToast(e.response?.data?.message || '注册失败')
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.register-header {
  text-align: center;
  padding: var(--space-5) var(--space-6) var(--space-4);
}
.register-welcome {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.register-hint {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.form-section {
  padding: 0 var(--space-2);
}
.section-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  padding: 0 var(--space-4) var(--space-2);
  letter-spacing: 0.3px;
}
.label-optional {
  font-weight: 400;
  color: var(--color-text-muted);
}
.profile-label {
  margin-top: 18px;
}

.captcha-box {
  height: 38px;
  width: 100px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  transition: border-color 0.2s;
}
.captcha-box:active {
  border-color: var(--color-brand);
}
.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.captcha-loading {
  font-size: 11px;
  color: var(--color-text-muted);
}

.submit-wrap {
  margin: var(--space-6) var(--space-4) 0;
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
  gap: var(--space-2);
  margin-top: 18px;
  padding-bottom: var(--space-6);
}
.switch-text {
  font-size: 13px;
  color: var(--color-text-muted);
}
</style>
