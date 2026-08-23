<script setup lang="ts">
import { zhCN } from "@/locales/zh-CN";

defineOptions({ name: "LoginShell" });
</script>

<template>
  <main class="login-shell">
    <aside class="login-shell__brand">
      <div class="login-shell__brand-inner">
        <div class="login-shell__mark" aria-hidden="true">
          <span>营</span>
        </div>
        <h1>{{ zhCN.appTitle }}</h1>
        <p class="login-shell__sub">{{ zhCN.consoleSubtitle }}</p>
        <ul class="login-shell__points">
          <li v-for="point in zhCN.login.points" :key="point">{{ point }}</li>
        </ul>
      </div>
    </aside>
    <section class="login-shell__panel">
      <div class="login-shell__card">
        <slot />
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-shell {
  min-height: 100vh;
  display: flex;
  background: var(--admin-page-bg);
}
.login-shell__brand {
  width: 42%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px 56px;
  color: #fff;
  background-color: var(--admin-aside);
  background-image:
    radial-gradient(900px 480px at 0% 100%, rgba(22, 119, 255, 0.28), transparent 60%),
    linear-gradient(165deg, #001529 0%, #001529 58%, #003a8c 100%);
}
.login-shell__brand-inner {
  width: 100%;
  max-width: 360px;
}
.login-shell__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-bottom: 24px;
  border-radius: 8px;
  background: var(--admin-primary);
  font-size: 18px;
  font-weight: 600;
  line-height: 1;
}
.login-shell__brand h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: 0.02em;
}
.login-shell__sub {
  margin: 8px 0 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.65);
}
.login-shell__points {
  margin: 32px 0 0;
  padding: 24px 0 0;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  list-style: none;
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  line-height: 1.5;
}
.login-shell__points li {
  position: relative;
  margin: 0;
  padding: 6px 0 6px 16px;
}
.login-shell__points li::before {
  content: "";
  position: absolute;
  top: 13px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--admin-primary);
}
.login-shell__panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
}
.login-shell__card {
  width: 400px;
  max-width: 100%;
  padding: 40px 40px 32px;
  border-radius: 8px;
  background: var(--admin-surface);
  box-shadow:
    0 6px 16px 0 rgba(0, 0, 0, 0.08),
    0 3px 6px -4px rgba(0, 0, 0, 0.12),
    0 9px 28px 8px rgba(0, 0, 0, 0.05);
}
@media (max-width: 768px) {
  .login-shell {
    flex-direction: column;
  }
  .login-shell__brand {
    width: 100%;
    padding: 28px 24px 24px;
  }
  .login-shell__brand-inner {
    max-width: none;
  }
  .login-shell__mark {
    margin-bottom: 16px;
  }
  .login-shell__brand h1 {
    font-size: 22px;
  }
  .login-shell__points {
    display: none;
  }
  .login-shell__panel {
    align-items: flex-start;
    padding: 24px 16px 32px;
  }
  .login-shell__card {
    width: 100%;
    padding: 28px 20px 24px;
    box-shadow: none;
  }
}
</style>
