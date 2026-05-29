<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">活动管理</span>
          <p class="page-sub">管理活动配置、规则和子模块</p>
        </div>
        <el-button type="primary" @click="$router.push('/activities/new')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建活动
        </el-button>
      </div>
    </template>

    <div class="filter-bar">
      <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
        <el-option label="全部状态" value="" />
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="在线" value="ONLINE" />
        <el-option label="已下线" value="OFFLINE" />
      </el-select>
      <el-button type="primary" @click="search" style="margin-left:8px">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="75" align="center" />
      <el-table-column prop="code" label="编码" min-width="140">
        <template #default="{ row }">
          <code class="code-cell">{{ row.code }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140">
        <template #default="{ row }">
          <span class="name-cell">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="描述" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.description" class="desc-cell">{{ truncate(row.description, 30) }}</span>
          <span v-else class="desc-empty">--</span>
        </template>
      </el-table-column>
      <el-table-column label="灰度" width="90" align="center">
        <template #default="{ row }">
          <span class="gray-pill">{{ grayLabel(row.grayType) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="105">
        <template #default="{ row }">
          <el-tooltip :content="statusTooltip(row.status)" placement="top" :show-after="300" effect="light">
            <span :class="['status-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="160">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.startTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" width="160">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.endTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <div class="action-cell">
            <el-button size="small" type="primary" @click="$router.push(`/activities/${row.id}`)">编辑</el-button>
            <el-button size="small" @click="$router.push(`/activities/sub-modules?activityCode=${row.code}`)">关联</el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, row)">
              <el-button size="small" text>
                更多
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="margin-left:2px;vertical-align:-1px"><polyline points="6 9 12 15 18 9"/></svg>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'" command="publish">发布</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'PUBLISHED' || row.status === 'ONLINE'" command="offline">下线</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'OFFLINE'" command="backToDraft">退回草稿</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'DRAFT'" command="delete" divided>
                    <span style="color:var(--el-color-danger)">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap" v-if="total > 0">
      <el-pagination
        v-model:current-page="pagination.page"
        :page-size="pagination.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'ActivityList' })
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listActivities, publishActivity, offlineActivity, backToDraftActivity, deleteActivity, type Activity } from '../../api/activity'

const rows = ref<Activity[]>([])
const loading = ref(false)
const total = ref(0)

const pagination = reactive({ page: 1, size: 20 })
const filters = reactive({ status: '' })

const statusLabel = (s: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', ONLINE: '在线', OFFLINE: '已下线' }[s] || s)
const statusClass = (s: string) => ({ DRAFT: 'draft', PUBLISHED: 'published', ONLINE: 'online', OFFLINE: 'offline' }[s] || '')
const grayLabel = (t: string) => ({ NONE: '全量', RATIO: '按比例', WHITELIST: '白名单' }[t] || t || '全量')

function statusTooltip(s: string) {
  return {
    DRAFT: '草稿：活动尚未发布，C端不可见',
    PUBLISHED: '已发布：活动预告可见，未到开始时间',
    ONLINE: '在线：活动进行中，用户可参与',
    OFFLINE: '已下线：活动已结束',
  }[s] || s
}

function truncate(text: string, max: number) {
  return text.length > max ? text.slice(0, max) + '...' : text
}

function formatTime(t: string | undefined) {
  if (!t) return '--'
  return t.replace('T', ' ').substring(0, 16)
}

async function load() {
  loading.value = true
  try {
    const params: { page: number; size: number; status?: string } = { page: pagination.page, size: pagination.size }
    if (filters.status) params.status = filters.status
    const { data } = await listActivities(params)
    rows.value = data.data.records
    total.value = data.data.total
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载活动列表失败')
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  load()
}

function reset() {
  filters.status = ''
  pagination.page = 1
  load()
}

function onSizeChange() {
  pagination.page = 1
  load()
}

function onPageChange() {
  load()
}

async function handlePublish(row: any) {
  try {
    await publishActivity(row.id)
    ElMessage.success('发布成功')
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '发布失败')
  }
}

async function handleOffline(row: any) {
  try {
    await offlineActivity(row.id)
    ElMessage.success('已下线')
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '下线失败')
  }
}

async function handleBackToDraft(row: any) {
  try {
    await backToDraftActivity(row.id)
    ElMessage.success('已退回草稿')
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '退回草稿失败')
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除活动「${row.name}」？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteActivity(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '删除失败')
    }
  }
}

function handleAction(command: string, row: any) {
  switch (command) {
    case 'publish': handlePublish(row); break
    case 'offline': handleOffline(row); break
    case 'backToDraft': handleBackToDraft(row); break
    case 'delete': handleDelete(row); break
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.page-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.code-cell {
  font-family: var(--font-mono);
  font-size: 11px;
  background: var(--color-brand-primary-subtle);
  color: var(--color-brand-primary);
  padding: 2px 6px;
  border-radius: 4px;
}
.name-cell {
  font-weight: 600;
  color: var(--color-text-primary);
}
.desc-cell {
  color: var(--color-text-muted);
  font-size: 12px;
}
.desc-empty {
  color: var(--color-text-disabled);
  font-size: 11px;
}
.time-cell {
  color: var(--color-text-muted);
  font-size: 12px;
  white-space: nowrap;
}
.gray-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  background: var(--color-brand-primary-subtle);
  color: var(--color-brand-primary);
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.6;
  cursor: default;
}
.status-pill::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.status-pill.published { background: var(--color-published-subtle); color: var(--color-published-text); }
.status-pill.published::before { background: var(--color-published-text); }
.status-pill.online { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.status-pill.online::before { background: var(--color-emerald-text); }
.status-pill.draft { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.status-pill.draft::before { background: var(--color-warning); }
.status-pill.offline { background: var(--color-border-light); color: var(--color-text-muted); }
.status-pill.offline::before { background: var(--color-text-disabled); }
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.action-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
