<template>
  <div class="point-page">
    <div class="point-hero">
      <div class="hero-bg">
        <div class="hero-orb orb-1"></div>
        <div class="hero-orb orb-2"></div>
        <div class="hero-orb orb-3"></div>
      </div>
      <div class="hero-content">
        <div class="hero-nav">
          <div class="nav-back" @click="$router.back()">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
          </div>
          <span class="nav-title">积分明细</span>
          <div style="width: 36px;"></div>
        </div>

        <div class="balance-display" v-if="account">
          <div class="balance-label">当前积分</div>
          <div class="balance-value">{{ account.balance }}</div>
          <div class="balance-deco">
            <span></span><span></span><span></span>
          </div>
        </div>

        <div class="balance-stats" v-if="account">
          <div class="stat-item">
            <div class="stat-icon earn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="18 15 12 9 6 15"/></svg>
            </div>
            <div class="stat-info">
              <span class="stat-value earn-val">{{ account.totalEarned }}</span>
              <span class="stat-label">累计获得</span>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon spend">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="6 9 12 15 18 9"/></svg>
            </div>
            <div class="stat-info">
              <span class="stat-value spend-val">{{ account.totalSpent }}</span>
              <span class="stat-label">累计消费</span>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon expired">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            </div>
            <div class="stat-info">
              <span class="stat-value expired-val">{{ account.totalExpired }}</span>
              <span class="stat-label">已过期</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="filter-tabs animate-in animate-in-delay-1">
      <div
        v-for="tab in filterTabs"
        :key="tab.key"
        :class="['filter-tab', { active: activeType === tab.key }]"
        @click="onTypeChange(tab.key)"
      >
        {{ tab.label }}
      </div>
    </div>

    <van-loading v-if="loading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

    <van-empty v-else-if="records.length === 0" description="暂无积分记录" />

    <div v-else class="records-list">
      <div
        v-for="(record, idx) in records"
        :key="record.id"
        :class="['record-item', 'animate-in', `animate-in-delay-${Math.min(idx + 1, 8)}`]"
      >
        <div :class="['record-icon', typeIconClass(record.type)]">
          <svg v-if="record.type === 'EARN'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="18 15 12 9 6 15"/></svg>
          <svg v-else-if="record.type === 'DEDUCT'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="6 9 12 15 18 9"/></svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
        </div>
        <div class="record-info">
          <div class="record-desc">{{ record.description || sourceLabel(record.sourceType) }}</div>
          <div class="record-time">{{ formatTime(record.createdAt) }}</div>
        </div>
        <div :class="['record-amount', record.type === 'EARN' ? 'amount-earn' : 'amount-deduct']">
          {{ record.type === 'EARN' ? '+' : '-' }}{{ record.amount }}
        </div>
      </div>
    </div>

    <div v-if="hasMore && !loading" class="load-more" @click="loadMore">
      <span>加载更多</span>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="6 9 12 15 18 9"/></svg>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { showToast } from '../../utils/toast'
import { getPointBalance, getPointTransactions, type PointAccount, type PointTransaction } from '../../api/signin'

const account = ref<PointAccount | null>(null)
const records = ref<PointTransaction[]>([])
const loading = ref(false)
const activeType = ref('')
const page = ref(1)
const pageSize = 20
const hasMore = ref(true)

const filterTabs = [
  { key: '', label: '全部' },
  { key: 'EARN', label: '获得' },
  { key: 'DEDUCT', label: '消费' },
  { key: 'EXPIRE', label: '过期' },
]

const typeIconClass = (t: string) => ({
  EARN: 'icon-earn',
  DEDUCT: 'icon-deduct',
  EXPIRE: 'icon-expire',
}[t] || '')

const sourceLabel = (s: string) => ({
  SIGNIN: '每日签到',
  SIGNIN_STREAK: '连续签到奖励',
  TASK_REWARD: '任务奖励',
  CATCH_UP: '补签消耗',
  ADMIN_GRANT: '管理员发放',
}[s] || s)

function formatTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function loadBalance() {
  try {
    const { data } = await getPointBalance()
    account.value = data.data
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '加载余额失败')
  }
}

async function loadRecords() {
  loading.value = true
  page.value = 1
  hasMore.value = true
  try {
    const params: any = { page: 1, size: pageSize }
    if (activeType.value) params.type = activeType.value
    const { data } = await getPointTransactions(params)
    const pageData = data.data
    records.value = pageData?.records || []
    hasMore.value = records.value.length >= pageSize
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '加载记录失败')
  } finally {
    loading.value = false
  }
}

function onTypeChange(key: string) {
  activeType.value = key
  loadRecords()
}

async function loadMore() {
  page.value++
  try {
    const params: any = { page: page.value, size: pageSize }
    if (activeType.value) params.type = activeType.value
    const { data } = await getPointTransactions(params)
    const pageData = data.data
    const newRecords = pageData?.records || []
    records.value.push(...newRecords)
    hasMore.value = newRecords.length >= pageSize
  } catch (e: any) {
    page.value--
    showToast.fail(e.response?.data?.message || '加载失败')
  }
}

onMounted(() => {
  loadBalance()
  loadRecords()
})
</script>

<style scoped>
.point-page { min-height: 100vh; background: var(--color-bg); }

.point-hero { position: relative; overflow: hidden; padding-bottom: 16px; }
.hero-bg { position: absolute; inset: 0; background: linear-gradient(135deg, #4f46e5 0%, #6366f1 40%, #818cf8 100%); }
.hero-orb { position: absolute; border-radius: 50%; filter: blur(60px); }
.orb-1 { width: 180px; height: 180px; background: rgba(167, 139, 250, 0.35); top: -50px; right: -30px; animation: float 10s ease-in-out infinite; }
.orb-2 { width: 140px; height: 140px; background: rgba(236, 72, 153, 0.25); bottom: -30px; left: -20px; animation: float 12s ease-in-out infinite 3s; }
.orb-3 { width: 100px; height: 100px; background: rgba(6, 182, 212, 0.2); top: 30%; left: 50%; animation: float 8s ease-in-out infinite 1s; }

.hero-content { position: relative; z-index: 1; padding: 12px 20px 0; }

.hero-nav { display: flex; justify-content: space-between; align-items: center; padding-bottom: 20px; }
.nav-back {
  width: 36px; height: 36px; border-radius: 12px;
  background: rgba(255,255,255,0.15); backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center;
  color: #fff; cursor: pointer; transition: all 0.2s ease;
}
.nav-back:active { transform: scale(0.92); }
.nav-title { font-size: 17px; font-weight: 700; color: #fff; }

.balance-display { text-align: center; margin-bottom: 24px; }
.balance-label { font-size: 13px; color: rgba(255,255,255,0.6); letter-spacing: 0.5px; }
.balance-value {
  font-size: 48px; font-weight: 900; color: #fff; margin-top: 4px;
  letter-spacing: -1px; line-height: 1.1;
  text-shadow: 0 2px 20px rgba(0,0,0,0.1);
}
.balance-deco { display: flex; justify-content: center; gap: 6px; margin-top: 10px; }
.balance-deco span { width: 20px; height: 3px; border-radius: 2px; background: rgba(255,255,255,0.3); }
.balance-deco span:nth-child(2) { width: 32px; background: rgba(255,255,255,0.6); }

.balance-stats { display: flex; gap: 8px; }
.stat-item {
  flex: 1; display: flex; align-items: center; gap: 8px;
  padding: 12px; background: rgba(255,255,255,0.1);
  backdrop-filter: blur(10px); border-radius: 14px;
  border: 1px solid rgba(255,255,255,0.08);
}
.stat-icon {
  width: 32px; height: 32px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.stat-icon.earn { background: rgba(16, 185, 129, 0.2); color: #34d399; }
.stat-icon.spend { background: rgba(239, 68, 68, 0.2); color: #f87171; }
.stat-icon.expired { background: rgba(148, 163, 184, 0.2); color: rgba(255,255,255,0.6); }
.stat-info { display: flex; flex-direction: column; }
.stat-value { font-size: 15px; font-weight: 700; color: #fff; line-height: 1.2; }
.earn-val { color: #34d399; }
.spend-val { color: #f87171; }
.expired-val { color: rgba(255,255,255,0.7); }
.stat-label { font-size: 10px; color: rgba(255,255,255,0.45); margin-top: 2px; }

.filter-tabs { display: flex; gap: 8px; padding: 0 16px 12px; overflow-x: auto; margin-top: -4px; position: relative; z-index: 2; }
.filter-tab {
  padding: 8px 16px; border-radius: var(--radius-round);
  background: var(--color-surface); font-size: 13px; font-weight: 500;
  color: var(--color-text-secondary); cursor: pointer; transition: all 0.2s ease;
  white-space: nowrap; box-shadow: var(--shadow-sm);
}
.filter-tab.active { background: var(--color-brand); color: #fff; font-weight: 600; box-shadow: var(--shadow-brand); }

.loading-wrap { margin-top: 60px; }

.records-list { padding: 0 16px; }
.record-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0; border-bottom: 1px solid var(--color-border-light);
}
.record-item:last-child { border-bottom: none; }

.record-icon {
  width: 40px; height: 40px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.icon-earn { background: linear-gradient(135deg, #d1fae5, #a7f3d0); color: #059669; }
.icon-deduct { background: linear-gradient(135deg, #fef2f2, #fecaca); color: #dc2626; }
.icon-expire { background: linear-gradient(135deg, #f1f5f9, #e2e8f0); color: #94a3b8; }

.record-info { flex: 1; min-width: 0; }
.record-desc { font-size: 14px; color: var(--color-text-primary); font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.record-time { font-size: 12px; color: var(--color-text-muted); margin-top: 3px; }

.record-amount { font-size: 17px; font-weight: 800; flex-shrink: 0; font-variant-numeric: tabular-nums; letter-spacing: -0.3px; }
.amount-earn { color: #059669; }
.amount-deduct { color: #dc2626; }

.load-more {
  display: flex; align-items: center; justify-content: center; gap: 4px;
  padding: 16px; font-size: 13px; color: var(--color-brand); cursor: pointer; font-weight: 600;
}
.load-more:active { opacity: 0.7; }
</style>
