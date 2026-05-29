<template>
  <div class="prize-page">
    <div class="prize-hero">
      <div class="hero-bg">
        <div class="hero-orb orb-1"></div>
        <div class="hero-orb orb-2"></div>
      </div>
      <div class="hero-content">
        <h1 class="hero-title">领奖专区</h1>
        <p class="hero-subtitle">查看你的奖品，及时领取</p>
      </div>
    </div>

    <div class="filter-tabs animate-in animate-in-delay-1">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        :class="['filter-tab', { active: activeTab === tab.key }]"
        @click="onTabChange(tab.key)"
      >
        <span class="tab-text">{{ tab.label }}</span>
        <span v-if="tab.badge" class="tab-badge">{{ tab.badge }}</span>
      </div>
    </div>

    <van-loading v-if="loading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

    <van-empty v-else-if="records.length === 0" :description="emptyDesc" image="gift-o" />

    <div v-else class="records-list">
      <div
        v-for="(record, idx) in records"
        :key="record.id"
        :class="['record-card', 'animate-in', `animate-in-delay-${Math.min(idx + 2, 8)}`]"
        @click="showDetail(record)"
      >
        <div :class="['card-left-accent', `accent-${record.status.toLowerCase()}`]"></div>
        <div class="card-body">
          <div class="card-top">
            <div class="record-icon-box" :class="`icon-${record.status.toLowerCase()}`">
              <img v-if="record.prizeIcon" :src="record.prizeIcon" class="record-icon" />
              <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
                <circle cx="12" cy="8" r="5"/><path d="M3 21l3-7h12l3 7"/>
              </svg>
            </div>
            <div class="record-info">
              <div class="record-name">{{ record.prizeName }}</div>
              <div class="record-meta">{{ typeLabel(record.prizeType) }} · x{{ record.quantity || 1 }}</div>
            </div>
            <span :class="['status-chip', statusChipClass(record.status)]">{{ statusLabel(record.status) }}</span>
          </div>

          <div class="card-bottom" v-if="record.status === 'WON' && record.expireTime">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            <span class="expire-text">{{ formatExpire(record.expireTime) }}</span>
          </div>
          <div class="card-bottom error" v-if="record.status === 'FAILED' && record.errorMessage">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            <span class="error-text">{{ record.errorMessage }}</span>
          </div>

          <div class="card-arrow">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--color-border)" stroke-width="2" stroke-linecap="round"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>
    </div>

    <van-action-sheet v-model:show="detailVisible" :title="detailRecord?.prizeName || '奖品详情'">
      <div class="detail-content" v-if="detailRecord">
        <div class="detail-image" v-if="detailRecord.prizeImage">
          <img :src="detailRecord.prizeImage" />
        </div>
        <div class="detail-info">
          <div class="detail-row">
            <span class="detail-label">奖品名称</span>
            <span class="detail-value">{{ detailRecord.prizeName }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">奖品类型</span>
            <span class="detail-value">{{ typeLabel(detailRecord.prizeType) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">数量</span>
            <span class="detail-value">x{{ detailRecord.quantity || 1 }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <span :class="['detail-status', statusChipClass(detailRecord.status)]">{{ statusLabel(detailRecord.status) }}</span>
          </div>
          <div class="detail-row" v-if="detailRecord.wonAt">
            <span class="detail-label">获得时间</span>
            <span class="detail-value">{{ formatTime(detailRecord.wonAt) }}</span>
          </div>
          <div class="detail-row" v-if="detailRecord.grantedAt">
            <span class="detail-label">发放时间</span>
            <span class="detail-value">{{ formatTime(detailRecord.grantedAt) }}</span>
          </div>
          <div class="detail-row" v-if="detailRecord.expireTime">
            <span class="detail-label">有效期至</span>
            <span class="detail-value">{{ formatTime(detailRecord.expireTime) }}</span>
          </div>
        </div>
        <div class="detail-actions">
          <van-button
            v-if="detailRecord.status === 'WON'"
            type="primary"
            round
            block
            :loading="claiming"
            @click="doClaim(detailRecord.id)"
            class="claim-btn"
          >
            立即领取
          </van-button>
          <span v-else class="no-action-text">{{ claimDisabledReason(detailRecord.status) }}</span>
        </div>
      </div>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showToast } from '../../utils/toast'
import { claimPrize, getPrizeRecords, type PrizeRecord } from '../../api/prize'

const records = ref<PrizeRecord[]>([])
const counts = ref<Record<string, number>>({})
const loading = ref(false)
const activeTab = ref('')
const claiming = ref(false)
const detailVisible = ref(false)
const detailRecord = ref<PrizeRecord | null>(null)

const tabs = computed(() => [
  { key: '', label: '全部', badge: totalBadge('') },
  { key: 'WON', label: '待领取', badge: totalBadge('WON') },
  { key: 'GRANTED', label: '已到账', badge: totalBadge('GRANTED') },
  { key: 'EXPIRED', label: '已过期', badge: totalBadge('EXPIRED') },
])

const emptyDesc = computed(() => {
  const map: Record<string, string> = { WON: '暂无待领取奖品', GRANTED: '暂无已到账奖品', EXPIRED: '暂无过期奖品' }
  return map[activeTab.value] || '暂无奖品记录'
})

function totalBadge(key: string) {
  const c = counts.value[key] || 0
  return c > 0 ? String(c) : ''
}

const typeLabel = (t: string) => {
  const map: Record<string, string> = { POINT: '积分', COUPON: '优惠券', BADGE: '徽章', PHYSICAL: '实物', MEMBERSHIP: '会员卡', INTERNAL: '其他' }
  return map[t] || t
}

const statusLabel = (s: string) => {
  const map: Record<string, string> = { WON: '待领取', CLAIMING: '领取中', GRANTED: '已到账', FAILED: '发放失败', FAILED_PERMANENTLY: '发放失败', EXPIRED: '已过期' }
  return map[s] || s
}

const statusChipClass = (s: string) => {
  const map: Record<string, string> = { WON: 'chip-won', GRANTED: 'chip-granted', CLAIMING: 'chip-claiming', FAILED: 'chip-failed', FAILED_PERMANENTLY: 'chip-failed', EXPIRED: 'chip-expired' }
  return map[s] || ''
}

function claimDisabledReason(s: string) {
  const map: Record<string, string> = { GRANTED: '奖品已到账', CLAIMING: '奖品领取中', FAILED: '发放失败，请联系客服', FAILED_PERMANENTLY: '发放永久失败，请联系客服', EXPIRED: '奖品已过期' }
  return map[s] || '当前不可领取'
}

function formatTime(t: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatExpire(t: string) {
  const d = new Date(t)
  const now = Date.now()
  const diff = d.getTime() - now
  if (diff <= 0) return '已过期'
  const hours = Math.ceil(diff / 3600000)
  if (hours < 24) return `${hours} 小时后过期`
  const days = Math.ceil(hours / 24)
  return `${days} 天后过期`
}

async function loadRecords() {
  loading.value = true
  try {
    const { data } = await getPrizeRecords(activeTab.value || undefined)
    records.value = data.data?.records || []
    counts.value = data.data?.counts || {}
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function onTabChange(key: string) {
  activeTab.value = key
  loadRecords()
}

function showDetail(record: PrizeRecord) {
  detailRecord.value = record
  detailVisible.value = true
}

async function doClaim(recordId: number) {
  claiming.value = true
  try {
    const { data } = await claimPrize(recordId)
    const result = data.data
    if (result.status === 'GRANTED') {
      showToast.success('领取成功')
      detailVisible.value = false
      loadRecords()
    } else if (result.status === 'FAILED') {
      showToast.fail(result.errorMessage || '领取失败')
    } else {
      showToast.info('正在处理中')
    }
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '领取失败')
  } finally {
    claiming.value = false
  }
}

onMounted(loadRecords)
</script>

<style scoped>
.prize-page { min-height: 100vh; background: var(--color-bg); }

.prize-hero { position: relative; overflow: hidden; padding-bottom: 20px; }
.hero-bg { position: absolute; inset: 0; background: linear-gradient(135deg, #f59e0b 0%, #f97316 50%, #ef4444 100%); }
.hero-orb { position: absolute; border-radius: 50%; filter: blur(60px); }
.orb-1 { width: 180px; height: 180px; background: rgba(236, 72, 153, 0.3); top: -50px; right: -30px; animation: float 10s ease-in-out infinite; }
.orb-2 { width: 140px; height: 140px; background: rgba(251, 191, 36, 0.3); bottom: -30px; left: -20px; animation: float 12s ease-in-out infinite 2s; }
.hero-content { position: relative; z-index: 1; padding: 48px 20px 0; }
.hero-title { margin: 0; font-size: 26px; font-weight: 800; color: #fff; }
.hero-subtitle { margin: 6px 0 0; font-size: 13px; color: rgba(255, 255, 255, 0.7); }

.filter-tabs { display: flex; gap: 8px; padding: 0 16px 12px; overflow-x: auto; margin-top: -8px; position: relative; z-index: 2; }
.filter-tab {
  display: flex; align-items: center; gap: 6px; padding: 8px 16px;
  border-radius: var(--radius-round); background: var(--color-surface);
  font-size: 13px; font-weight: 500; color: var(--color-text-secondary);
  cursor: pointer; transition: all 0.2s ease; white-space: nowrap; box-shadow: var(--shadow-sm);
}
.filter-tab.active { background: var(--color-brand); color: #fff; font-weight: 600; box-shadow: var(--shadow-brand); }
.tab-badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px;
  font-size: 10px; font-weight: 700; background: rgba(255,255,255,0.25); color: #fff;
}
.filter-tab:not(.active) .tab-badge { background: var(--color-brand-subtle); color: var(--color-brand); }

.loading-wrap { margin-top: 60px; }

.records-list { padding: 0 16px 16px; display: flex; flex-direction: column; gap: 10px; }
.record-card {
  background: var(--color-surface); border-radius: var(--radius-lg); overflow: hidden;
  box-shadow: var(--shadow-card); transition: all 0.3s ease; display: flex;
}
.record-card:active { transform: scale(0.98); box-shadow: var(--shadow-card-hover); }

.card-left-accent { width: 4px; flex-shrink: 0; }
.accent-won { background: linear-gradient(180deg, #f59e0b, #fbbf24); }
.accent-granted { background: linear-gradient(180deg, #10b981, #34d399); }
.accent-claiming { background: linear-gradient(180deg, #6366f1, #818cf8); }
.accent-failed { background: linear-gradient(180deg, #ef4444, #f87171); }
.accent-expired { background: linear-gradient(180deg, #94a3b8, #cbd5e1); }

.card-body { flex: 1; padding: 14px 14px 14px 16px; position: relative; }
.card-top { display: flex; align-items: center; gap: 12px; }

.record-icon-box {
  width: 44px; height: 44px; border-radius: 12px; display: flex;
  align-items: center; justify-content: center; flex-shrink: 0; overflow: hidden;
}
.icon-won { background: linear-gradient(135deg, #fef3c7, #fde68a); color: #d97706; }
.icon-granted { background: linear-gradient(135deg, #d1fae5, #a7f3d0); color: #059669; }
.icon-claiming { background: linear-gradient(135deg, #eef2ff, #c7d2fe); color: #6366f1; }
.icon-failed { background: linear-gradient(135deg, #fef2f2, #fecaca); color: #dc2626; }
.icon-expired { background: linear-gradient(135deg, #f1f5f9, #e2e8f0); color: #94a3b8; }
.record-icon { width: 100%; height: 100%; object-fit: cover; }

.record-info { flex: 1; min-width: 0; }
.record-name { font-size: 15px; font-weight: 600; color: var(--color-text-primary); line-height: 1.3; }
.record-meta { font-size: 12px; color: var(--color-text-muted); margin-top: 3px; }

.status-chip {
  font-size: 11px; font-weight: 600; padding: 4px 10px;
  border-radius: var(--radius-round); white-space: nowrap; flex-shrink: 0;
}
.chip-won { background: #fef3c7; color: #92400e; }
.chip-granted { background: #d1fae5; color: #065f46; }
.chip-claiming { background: #eef2ff; color: #4338ca; }
.chip-failed { background: #fef2f2; color: #dc2626; }
.chip-expired { background: #f1f5f9; color: #64748b; }

.card-bottom { display: flex; align-items: center; gap: 4px; margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--color-border-light); font-size: 12px; }
.expire-text { color: #d97706; }
.error-text { color: #dc2626; }
.card-arrow { position: absolute; right: 14px; top: 50%; transform: translateY(-50%); }

.detail-content { padding: 0 20px 24px; }
.detail-image { width: 100%; max-height: 180px; border-radius: var(--radius-md); overflow: hidden; margin-bottom: 16px; }
.detail-image img { width: 100%; height: 100%; object-fit: cover; }
.detail-row { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--color-border-light); font-size: 14px; }
.detail-label { color: var(--color-text-secondary); }
.detail-value { color: var(--color-text-primary); font-weight: 500; }
.detail-status { font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: var(--radius-round); }
.detail-actions { margin-top: 16px; }
.claim-btn { height: 48px !important; font-size: 16px !important; font-weight: 700 !important; border-radius: 14px !important; }
.no-action-text { display: block; text-align: center; color: var(--color-text-muted); font-size: 13px; padding: 16px 0; }
</style>
