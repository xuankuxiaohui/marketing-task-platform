<template>
  <div class="point-page">
    <van-nav-bar title="积分明细" />

    <!-- Balance card -->
    <div class="balance-card" v-if="account">
      <div class="balance-main">
        <div class="balance-label">当前积分</div>
        <div class="balance-value">{{ account.balance }}</div>
      </div>
      <div class="balance-stats">
        <div class="stat-item">
          <span class="stat-value">{{ account.totalEarned }}</span>
          <span class="stat-label">累计获得</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ account.totalSpent }}</span>
          <span class="stat-label">累计消费</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ account.totalExpired }}</span>
          <span class="stat-label">已过期</span>
        </div>
      </div>
    </div>

    <!-- Type filter tabs -->
    <van-tabs v-model:active="activeType" @change="loadRecords" sticky offset-top="46">
      <van-tab name="" title="全部" />
      <van-tab name="EARN" title="获得" />
      <van-tab name="DEDUCT" title="消费" />
      <van-tab name="EXPIRE" title="过期" />
    </van-tabs>

    <van-loading v-if="loading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

    <van-empty v-else-if="records.length === 0" description="暂无积分记录" />

    <div v-else class="records-list">
      <div v-for="record in records" :key="record.id" class="record-item">
        <div class="record-left">
          <div :class="['record-icon', typeIconClass(record.type)]">
            <van-icon :name="typeIcon(record.type)" size="18" />
          </div>
          <div class="record-info">
            <div class="record-desc">{{ record.description || sourceLabel(record.sourceType) }}</div>
            <div class="record-time">{{ formatTime(record.createdAt) }}</div>
          </div>
        </div>
        <div :class="['record-amount', record.type === 'EARN' ? 'amount-earn' : 'amount-deduct']">
          {{ record.type === 'EARN' ? '+' : '-' }}{{ record.amount }}
        </div>
      </div>
    </div>

    <!-- Load more -->
    <div v-if="hasMore && !loading" class="load-more" @click="loadMore">加载更多</div>
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

const typeIcon = (t: string) => ({
  EARN: 'plus',
  DEDUCT: 'minus',
  EXPIRE: 'clock-o',
}[t] || 'info-o')

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
.point-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.loading-wrap {
  margin-top: 60px;
}

.balance-card {
  margin: 12px 14px;
  padding: 20px;
  background: var(--color-brand-gradient);
  border-radius: var(--radius-lg);
  color: #fff;
}
.balance-main {
  text-align: center;
  margin-bottom: 16px;
}
.balance-label {
  font-size: 13px;
  opacity: 0.8;
}
.balance-value {
  font-size: 40px;
  font-weight: 700;
  margin-top: 4px;
}
.balance-stats {
  display: flex;
  justify-content: space-around;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}
.stat-item {
  text-align: center;
}
.stat-value {
  display: block;
  font-size: 18px;
  font-weight: 600;
}
.stat-label {
  display: block;
  font-size: 11px;
  opacity: 0.7;
  margin-top: 2px;
}

.records-list {
  padding: 0 14px;
}
.record-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border-light);
}
.record-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.record-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.icon-earn {
  background: var(--color-granted-bg);
  color: var(--color-granted);
}
.icon-deduct {
  background: var(--color-failed-bg);
  color: var(--color-failed);
}
.icon-expire {
  background: var(--color-status-expired-bg);
  color: var(--color-status-expired);
}
.record-info {
  flex: 1;
  min-width: 0;
}
.record-desc {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.record-time {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 2px;
}
.record-amount {
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.amount-earn {
  color: var(--color-granted);
}
.amount-deduct {
  color: var(--color-failed);
}

.load-more {
  text-align: center;
  padding: 16px;
  font-size: 13px;
  color: var(--color-brand);
  cursor: pointer;
}
</style>
