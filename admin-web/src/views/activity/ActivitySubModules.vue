<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">活动关联</span>
          <p class="page-sub">查看活动关联的任务、签到和奖品</p>
        </div>
      </div>
    </template>

    <div class="picker-section">
      <span class="picker-label">选择活动</span>
      <ActivityPicker v-model="activityCode" placeholder="请选择活动" />
    </div>

    <div v-if="!activityCode" class="empty-state">
      <el-empty description="请先选择一个活动" />
    </div>

    <div v-else v-loading="loading">
      <el-empty v-if="!subModules" description="暂无数据" />

      <template v-if="subModules">
        <el-divider content-position="left">关联任务</el-divider>
        <el-table :data="subModules.tasks" size="small" empty-text="无关联任务">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <span :class="['status-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">关联签到</el-divider>
        <el-table :data="subModules.signInConfigs" size="small" empty-text="无关联签到">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="200" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <span :class="['status-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">关联奖品</el-divider>
        <el-table :data="subModules.prizes" size="small" empty-text="无关联奖品">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="type" label="类型" width="100" />
        </el-table>

        <el-divider content-position="left">
          奖品记录
          <el-button size="small" text type="primary" style="margin-left:8px" @click="$router.push(`/prize-records?activityCode=${activityCode}`)">查看全部</el-button>
        </el-divider>
        <div class="prize-stats" v-if="prizeStats">
          <div class="stat-item">
            <span class="stat-value">{{ prizeStats.total }}</span>
            <span class="stat-label">总记录</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-won">{{ prizeStats.won }}</span>
            <span class="stat-label">待领取</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-granted">{{ prizeStats.granted }}</span>
            <span class="stat-label">已到账</span>
          </div>
          <div class="stat-item">
            <span class="stat-value stat-failed">{{ prizeStats.failed }}</span>
            <span class="stat-label">失败</span>
          </div>
        </div>
        <el-table :data="prizeRecords" size="small" empty-text="暂无奖品记录" v-loading="loadingRecords">
          <el-table-column prop="id" label="记录ID" width="80" />
          <el-table-column prop="userId" label="用户ID" width="120" />
          <el-table-column prop="prizeName" label="奖品名称" min-width="120" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <span :class="['status-pill', recordStatusClass(row.status)]">{{ recordStatusLabel(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="wonAt" label="获得时间" width="160">
            <template #default="{ row }">
              <span class="time-cell">{{ row.wonAt ?? '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'ActivitySubModules' })
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listActivities, getSubModules, type ActivitySubModules } from '../../api/activity'
import { listPrizeRecords } from '../../api/prize'
import ActivityPicker from '../../components/ActivityPicker.vue'

const route = useRoute()
const router = useRouter()

const activityCode = ref('')
const subModules = ref<ActivitySubModules | null>(null)
const loading = ref(false)

const prizeRecords = ref<any[]>([])
const loadingRecords = ref(false)
const prizeStats = ref<{ total: number; won: number; granted: number; failed: number } | null>(null)

const recordStatusLabel = (s: string) => ({
  WON: '待领取', CLAIMING: '领取中', GRANTED: '已到账',
  FAILED: '失败', FAILED_PERMANENTLY: '永久失败', EXPIRED: '已过期'
}[s] || s)

const recordStatusClass = (s: string) => ({
  WON: 's-won', CLAIMING: 's-claiming', GRANTED: 's-granted',
  FAILED: 's-failed', FAILED_PERMANENTLY: 's-permfail', EXPIRED: 's-expired'
}[s] || '')

const statusLabel = (s: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', ONLINE: '在线', OFFLINE: '已下线' }[s] || s)
const statusClass = (s: string) => ({ DRAFT: 'draft', PUBLISHED: 'published', ONLINE: 'online', OFFLINE: 'offline' }[s] || '')

async function loadSubModules(code: string) {
  loading.value = true
  subModules.value = null
  prizeRecords.value = []
  prizeStats.value = null
  try {
    const { data: listData } = await listActivities({ page: 1, size: 1000 })
    const activity = (listData.data?.records ?? []).find((a: any) => a.code === code)
    if (!activity) {
      ElMessage.warning('未找到该活动')
      return
    }
    const { data } = await getSubModules(activity.id)
    subModules.value = data.data
    await loadPrizeRecords(code)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载关联数据失败')
  } finally {
    loading.value = false
  }
}

async function loadPrizeRecords(code: string) {
  loadingRecords.value = true
  try {
    const { data } = await listPrizeRecords({ activityCode: code, page: 1, size: 10 })
    const records = data.data?.records || []
    prizeRecords.value = records
    const total = data.data?.total || 0
    // Count stats from all records (use the total + status breakdown from the page)
    const won = records.filter((r: any) => r.status === 'WON').length
    const granted = records.filter((r: any) => r.status === 'GRANTED').length
    const failed = records.filter((r: any) => r.status === 'FAILED' || r.status === 'FAILED_PERMANENTLY').length
    prizeStats.value = { total, won, granted, failed }
  } catch (e: any) {
    console.error('Failed to load prize records:', e)
  } finally {
    loadingRecords.value = false
  }
}

watch(activityCode, (code) => {
  if (code) {
    router.replace({ query: { activityCode: code } })
    loadSubModules(code)
  } else {
    subModules.value = null
    router.replace({ query: {} })
  }
})

onMounted(() => {
  const queryCode = route.query.activityCode as string
  if (queryCode) {
    activityCode.value = queryCode
  }
})
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
.picker-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 400px;
}
.picker-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
}
.empty-state {
  padding: 40px 0;
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

/* prize stats */
.prize-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.stat-won { color: var(--color-brand-primary); }
.stat-granted { color: var(--color-emerald-text); }
.stat-failed { color: var(--color-danger); }
.stat-label {
  font-size: 11px;
  color: var(--color-text-muted);
}

/* prize record status pills */
.s-won { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.s-claiming { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.s-granted { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.s-failed { background: var(--color-danger-subtle); color: var(--color-danger); }
.s-permfail { background: var(--color-pink-subtle); color: var(--color-pink-text); }
.s-expired { background: var(--color-border-light); color: var(--color-text-disabled); }

.time-cell {
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
