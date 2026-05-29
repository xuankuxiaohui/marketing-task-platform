<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">签到活动</span>
          <p class="page-sub">管理签到活动配置、积分发放和补签规则</p>
        </div>
        <el-button type="primary" @click="$router.push('/signin-configs/new')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建活动
        </el-button>
      </div>
    </template>

    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="状态">
        <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px" @change="search">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" placeholder="活动名称" clearable style="width: 180px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="活动名称" min-width="160">
        <template #default="{ row }">
          <span class="name-cell">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="activityCode" label="活动编码" width="140">
        <template #default="{ row }">
          <span class="code-cell">{{ row.activityCode ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="周期" width="80" align="center">
        <template #default="{ row }">
          <span :class="['type-pill', row.periodType === 'WEEKLY' ? 't-weekly' : 't-monthly']">
            {{ row.periodType === 'WEEKLY' ? '按周' : '按月' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="basePoints" label="基础积分" width="90" align="center">
        <template #default="{ row }">
          <span class="num-cell">{{ row.basePoints }}</span>
        </template>
      </el-table-column>
      <el-table-column label="补签" width="80" align="center">
        <template #default="{ row }">
          <span :class="row.catchUpEnabled ? 'bool-on' : 'bool-off'">{{ row.catchUpEnabled ? '开启' : '关闭' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <span :class="['status-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="时间窗口" min-width="180">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="$router.push(`/signin-configs/${row.id}`)">编辑</el-button>
          <el-button v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'" size="small" type="success" plain @click="handlePublish(row.id)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" size="small" type="warning" plain @click="handleOffline(row.id)">下线</el-button>
          <el-button size="small" plain @click="showStats(row)">统计</el-button>
          <el-popconfirm v-if="row.status !== 'PUBLISHED'" title="确认删除？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger" plain>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- Stats drawer -->
    <el-drawer v-model="statsVisible" :title="`统计数据 — ${statsConfig?.name || ''}`" size="400px">
      <template v-if="statsData">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="今日签到">{{ statsData.todaySigned }}</el-descriptions-item>
          <el-descriptions-item label="累计签到">{{ statsData.totalSigned }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <div class="stats-records-header">
          <span class="stats-records-title">签到记录</span>
          <el-input v-model="recordsUserId" placeholder="按用户ID筛选" clearable size="small" style="width:180px" @keyup.enter="loadRecords" />
        </div>
        <el-table :data="records" v-loading="recordsLoading" size="small" max-height="400">
          <el-table-column prop="userId" label="用户" width="120" />
          <el-table-column prop="signinDate" label="日期" width="110" />
          <el-table-column prop="streakDay" label="连续" width="60" align="center" />
          <el-table-column prop="totalPoints" label="积分" width="70" align="center" />
          <el-table-column label="补签" width="60" align="center">
            <template #default="{ row }">
              <span :class="row.catchUp ? 'bool-on' : 'bool-off'">{{ row.catchUp ? '是' : '否' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'SignInConfigList' })
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listSignInConfigs, publishSignInConfig, offlineSignInConfig, deleteSignInConfig,
  getSignInConfigStats, getSignInRecords,
  type SignInConfig, type SignInRecord, type ConfigStats,
} from '../../api/signin'

const rows = ref<SignInConfig[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ status: '', keyword: '' })

const statsVisible = ref(false)
const statsConfig = ref<SignInConfig | null>(null)
const statsData = ref<ConfigStats | null>(null)
const records = ref<SignInRecord[]>([])
const recordsLoading = ref(false)
const recordsUserId = ref('')

const statusLabel = (s: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }[s] || s)
const statusClass = (s: string) => ({ DRAFT: 's-draft', PUBLISHED: 's-published', OFFLINE: 's-offline' }[s] || '')

function formatTime(t?: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 16)
}

async function load() {
  loading.value = true
  try {
    const { data } = await listSignInConfigs({ page: page.value, size: size.value, ...filters })
    rows.value = data.data.records || []
    total.value = data.data.total || 0
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }
function reset() { filters.status = ''; filters.keyword = ''; search() }

async function handlePublish(id: number) {
  try { await publishSignInConfig(id); ElMessage.success('已发布'); load() }
  catch (e: any) { ElMessage.error(e.response?.data?.message || '发布失败') }
}

async function handleOffline(id: number) {
  try { await offlineSignInConfig(id); ElMessage.success('已下线'); load() }
  catch (e: any) { ElMessage.error(e.response?.data?.message || '下线失败') }
}

async function handleDelete(id: number) {
  try { await deleteSignInConfig(id); ElMessage.success('已删除'); load() }
  catch (e: any) { ElMessage.error(e.response?.data?.message || '删除失败') }
}

async function showStats(config: SignInConfig) {
  statsConfig.value = config
  statsVisible.value = true
  statsData.value = null
  records.value = []
  recordsUserId.value = ''
  try {
    const { data } = await getSignInConfigStats(config.id!)
    statsData.value = data.data
    await loadRecords()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载统计失败')
  }
}

async function loadRecords() {
  if (!statsConfig.value?.id) return
  recordsLoading.value = true
  try {
    const params: any = { page: 1, size: 50 }
    if (recordsUserId.value.trim()) params.userId = recordsUserId.value.trim()
    const { data } = await getSignInRecords(statsConfig.value.id, params)
    records.value = data.data.records || []
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载记录失败')
  } finally {
    recordsLoading.value = false
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
  margin-bottom: 12px;
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
.num-cell {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}
.time-cell {
  font-size: 12px;
  color: var(--color-text-muted);
}

.type-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
}
.t-weekly { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.t-monthly { background: var(--el-color-success-light-5); color: var(--el-color-success); }

.status-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
}
.s-draft { background: var(--color-border-light); color: var(--color-text-muted); }
.s-published { background: var(--el-color-success-light-5); color: var(--el-color-success); }
.s-offline { background: var(--el-color-warning-light-5); color: var(--el-color-warning); }

.bool-on { color: var(--color-published-text); font-weight: 600; font-size: 12px; }
.bool-off { color: var(--color-text-disabled); font-size: 12px; }

.pager-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.stats-records-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.stats-records-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-primary);
}
</style>
