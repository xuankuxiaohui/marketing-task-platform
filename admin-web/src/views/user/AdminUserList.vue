<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">后台用户管理</span>
          <p class="page-sub">管理系统后台用户账号</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">新建用户</el-button>
      </div>
    </template>

    <el-form :inline="true" class="filter-bar">
      <el-form-item label="关键字">
        <el-input v-model="filters.keyword" placeholder="用户名/昵称" clearable style="width:200px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="75" align="center" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
            {{ row.enabled ? '正常' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-popconfirm title="确定重置密码？" @confirm="handleResetPassword(row.id)">
            <template #reference>
              <el-button size="small" type="warning" plain>重置密码</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm :title="row.enabled ? '确定停用该用户？' : '确定启用该用户？'" @confirm="handleToggleEnabled(row.id)">
            <template #reference>
              <el-button size="small" :type="row.enabled ? 'danger' : 'success'" plain>
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-if="row.enabled" title="确定踢下线？" @confirm="handleKick(row.id)">
            <template #reference>
              <el-button size="small" type="info" plain>踢下线</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap" v-if="total > 0">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>

    <el-dialog v-model="createDialogVisible" title="新建管理员" width="420px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="3-32个字符" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="至少6个字符" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createForm.nickname" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'AdminUserList' })
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAdminUsers, resetAdminUserPassword, toggleAdminUserEnabled, kickAdminUser, createAdminUser } from '../../api/admin-user'

const rows = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({ page: 1, size: 20 })
const filters = reactive({ keyword: '' })

const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ username: '', password: '', nickname: '' })
const createRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '长度3-32个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '长度6-64个字符', trigger: 'blur' },
  ],
}

const formatTime = (t: string | null | undefined) => {
  if (!t) return '--'
  return t.replace('T', ' ').substring(0, 19)
}

async function load() {
  loading.value = true
  try {
    const params: any = { page: pagination.page, size: pagination.size }
    if (filters.keyword) params.keyword = filters.keyword
    const { data } = await listAdminUsers(params)
    rows.value = data.data.records || []
    total.value = data.data.total || 0
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function search() { pagination.page = 1; load() }
function reset() { filters.keyword = ''; pagination.page = 1; load() }
function onPageChange() { load() }
function onSizeChange() { pagination.page = 1; load() }

async function handleResetPassword(id: number) {
  try {
    const { data } = await resetAdminUserPassword(id)
    await ElMessageBox.alert(`新密码：<strong style="font-family:monospace;font-size:16px;color:var(--el-color-primary)">${data.data}</strong><br/>请妥善保存并告知用户。`, '密码重置成功', {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '知道了',
    })
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '重置密码失败')
  }
}

async function handleToggleEnabled(id: number) {
  try {
    await toggleAdminUserEnabled(id)
    ElMessage.success('操作成功')
    load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function handleKick(id: number) {
  try {
    await kickAdminUser(id)
    ElMessage.success('已踢下线')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

function openCreateDialog() {
  createForm.username = ''
  createForm.password = ''
  createForm.nickname = ''
  createDialogVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  createLoading.value = true
  try {
    await createAdminUser({
      username: createForm.username,
      password: createForm.password,
      nickname: createForm.nickname || undefined,
    })
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    createLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: flex-start; }
.page-title { font-size: 16px; font-weight: 700; color: var(--color-text-primary); }
.page-sub { margin: 2px 0 0; font-size: 12px; color: var(--color-text-muted); }
.filter-bar { margin-bottom: 16px; padding: 16px; background: var(--color-surface-raised); border-radius: 8px; border: 1px solid var(--color-border); }
.filter-bar :deep(.el-form-item) { margin-bottom: 8px; }
.time-cell { font-size: 12px; color: var(--color-text-secondary); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
