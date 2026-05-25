<template>
  <div class="prize-page">
    <van-nav-bar title="领奖专区" />

    <van-tabs v-model:active="activeTab" @change="onTabChange" sticky offset-top="46">
      <van-tab v-for="tab in tabs" :key="tab.key" :name="tab.key" :badge="tab.badge">
        <template #title>
          <span :class="{ 'tab-active': activeTab === tab.key }">{{ tab.label }}</span>
        </template>
      </van-tab>
    </van-tabs>

    <van-loading v-if="loading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

    <van-empty v-else-if="records.length === 0" :description="emptyDesc" />

    <div v-else class="records-list">
      <div
        v-for="record in records"
        :key="record.id"
        :class="['record-card', statusCardBg(record.status)]"
        @click="showDetail(record)"
      >
        <div class="record-header">
          <span :class="['status-tag', statusTagClass(record.status)]">{{ statusLabel(record.status) }}</span>
          <span class="record-time" v-if="record.wonAt">{{ formatTime(record.wonAt) }}</span>
        </div>

        <div class="record-body">
          <div class="record-icon-box" v-if="record.prizeIcon">
            <img :src="record.prizeIcon" class="record-icon" />
          </div>
          <div class="record-icon-box icon-fallback" v-else>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
              <circle cx="12" cy="8" r="5"/><path d="M3 21l3-7h12l3 7"/>
            </svg>
          </div>
          <div class="record-info">
            <div class="record-name">{{ record.prizeName }}</div>
            <div class="record-type">{{ typeLabel(record.prizeType) }} · x{{ record.quantity || 1 }}</div>
          </div>
          <div class="record-arrow">
            <van-icon name="arrow" size="14" color="var(--color-border)" />
          </div>
        </div>

        <div class="record-footer" v-if="record.status === 'WON' && record.expireTime">
          <span class="expire-hint">{{ formatExpire(record.expireTime) }}</span>
        </div>
        <div class="record-footer" v-if="record.status === 'FAILED' && record.errorMessage">
          <span class="error-hint">{{ record.errorMessage }}</span>
        </div>
      </div>
    </div>

    <!-- Detail popup -->
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
            <span :class="['detail-status', statusTagClass(detailRecord.status)]">{{ statusLabel(detailRecord.status) }}</span>
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

const statusTagClass = (s: string) => {
  const map: Record<string, string> = { WON: 's-won', GRANTED: 's-granted', CLAIMING: 's-claiming', FAILED: 's-failed', FAILED_PERMANENTLY: 's-failed', EXPIRED: 's-expired' }
  return map[s] || ''
}

const statusCardBg = (s: string) => {
  const map: Record<string, string> = { WON: 'card-won', GRANTED: 'card-granted', EXPIRED: 'card-expired' }
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

function onTabChange() {
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
.prize-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.tab-active {
  font-weight: 600;
}

.loading-wrap {
  margin-top: 60px;
}

.records-list {
  padding: var(--space-3) 14px;
}

.record-card {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  margin-bottom: 10px;
  padding: 14px;
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-fast);
}
.record-card:active {
  transform: scale(0.985);
}
.record-card.card-won {
  border-left: 4px solid var(--color-won);
}
.record-card.card-granted {
  border-left: 4px solid var(--color-granted);
}
.record-card.card-expired {
  border-left: 4px solid var(--color-status-expired);
  opacity: 0.7;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.status-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px var(--space-2);
  border-radius: var(--radius-lg);
}
.s-won { background: var(--color-won-bg); color: var(--color-won-text); }
.s-granted { background: var(--color-granted-bg); color: var(--color-granted-text); }
.s-claiming { background: var(--color-claiming-bg); color: var(--color-claiming-text); }
.s-failed { background: var(--color-failed-bg); color: var(--color-failed-text); }
.s-expired { background: var(--color-status-expired-bg); color: var(--color-status-expired-text); }

.record-time {
  font-size: 11px;
  color: var(--color-text-muted);
}

.record-body {
  display: flex;
  align-items: center;
  gap: 10px;
}

.record-icon-box {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}
.record-icon-box.icon-fallback {
  background: var(--color-border-light);
  color: var(--color-text-muted);
}
.record-icon {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.record-info {
  flex: 1;
  min-width: 0;
}
.record-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.record-type {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.record-arrow {
  flex-shrink: 0;
}

.record-footer {
  margin-top: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--color-border-light);
}
.expire-hint {
  font-size: 11px;
  color: var(--color-once-icon);
}
.error-hint {
  font-size: 11px;
  color: var(--color-failed);
}

/* Detail popup */
.detail-content {
  padding: 0 var(--space-4) var(--space-5);
}
.detail-image {
  width: 100%;
  max-height: 180px;
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: var(--space-3);
}
.detail-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.detail-info {
  margin-bottom: var(--space-4);
}
.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--color-border-light);
  font-size: 13px;
}
.detail-label {
  color: var(--color-text-secondary);
}
.detail-value {
  color: var(--color-text-primary);
  font-weight: 500;
}
.detail-status {
  font-size: 11px;
  font-weight: 600;
  padding: 2px var(--space-2);
  border-radius: var(--radius-lg);
}
.detail-actions {
  margin-top: var(--space-2);
}
.no-action-text {
  display: block;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
  padding: var(--space-3) 0;
}
</style>
