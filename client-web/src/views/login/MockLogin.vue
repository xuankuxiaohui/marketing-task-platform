<template>
  <div class="login-page">
    <van-nav-bar title="切换用户" left-arrow @click-left="$router.back()" />
    <van-form @submit="save" style="margin-top: 16px">
      <van-cell-group inset>
        <van-field v-model="user.userId" label="User ID" placeholder="u_demo" />
        <van-field v-model="user.province" label="省份" placeholder="BJ" />
        <van-field v-model="user.role" label="角色" placeholder="vip" />
        <van-field v-model="user.tags" label="标签" placeholder="vip,active" />
        <van-field v-model="user.orgId" label="组织" placeholder="org_001" />
        <van-field v-model="user.level" label="等级" type="digit" placeholder="5" />
        <van-field v-model="user.platform" label="平台" placeholder="WEB" />
      </van-cell-group>
      <div style="margin: 16px">
        <van-button round block type="primary" native-type="submit">保存并返回</van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()

const user = reactive({ ...userStore.$state })

function save() {
  userStore.$patch(user)
  showToast('已切换用户')
  router.back()
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #f7f8fa;
}
</style>
