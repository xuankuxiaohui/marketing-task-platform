<template>
  <el-container class="layout">
    <el-aside width="220px">
      <div class="brand">营销任务平台</div>
      <el-menu router :default-active="$route.path">
        <el-menu-item index="/tasks">任务管理</el-menu-item>
        <el-menu-item index="/instances">实例查询</el-menu-item>
        <el-menu-item index="/login">Mock 用户上下文</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>运营后台</el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.brand {
  padding: 18px;
  font-weight: 700;
}
el-header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #eee;
}
</style>
