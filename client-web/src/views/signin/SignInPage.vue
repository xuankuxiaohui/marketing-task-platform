<template>
  <div class="signin-page">
    <van-nav-bar title="签到">
      <template #right>
        <van-icon name="balance-o" size="20" @click="$router.push('/points')" />
      </template>
    </van-nav-bar>

    <van-loading v-if="initialLoading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

    <template v-else-if="configs.length === 0">
      <van-empty description="暂无签到活动" />
    </template>

    <template v-else>
      <!-- Config selector (if multiple) -->
      <div v-if="configs.length > 1" class="config-tabs">
        <div
          v-for="c in configs"
          :key="c.id"
          :class="['config-tab', { active: currentConfig?.id === c.id }]"
          @click="switchConfig(c)"
        >
          {{ c.name }}
        </div>
      </div>

      <!-- Status card -->
      <div class="status-card" v-if="status">
        <div class="status-top">
          <div class="balance-section">
            <div class="balance-label">积分余额</div>
            <div class="balance-value">{{ status.pointBalance }}</div>
          </div>
          <div class="streak-section">
            <div class="streak-value">{{ status.currentStreak }}</div>
            <div class="streak-label">连续签到</div>
          </div>
        </div>

        <!-- Tier progress -->
        <div class="tier-progress" v-if="status.nextTierDay">
          <div class="tier-hint">
            再签 <strong>{{ status.nextTierDay - status.currentStreak }}</strong> 天可获
            <strong>{{ status.nextTierBonus }}</strong> 积分奖励
          </div>
          <div class="tier-bar-bg">
            <div class="tier-bar-fill" :style="{ width: tierPercent + '%' }"></div>
          </div>
          <div class="tier-bar-labels">
            <span>0</span>
            <span>{{ status.nextTierDay }}天</span>
          </div>
        </div>
      </div>

      <!-- Calendar -->
      <div class="calendar-section">
        <div class="calendar-header">
          <van-icon name="arrow-left" size="18" @click="prevPeriod" />
          <span class="calendar-title">{{ calendar?.periodKey || '' }}</span>
          <van-icon name="arrow" size="18" @click="nextPeriod" />
        </div>

        <div class="calendar-grid" v-if="calendar">
          <div class="calendar-day-header" v-for="d in weekDays" :key="d">{{ d }}</div>
          <div
            v-for="(day, i) in calendarDays"
            :key="i"
            :class="['calendar-day', { empty: !day.date, signed: day.signed, today: day.isToday, catchup: day.catchUp }]"
          >
            <template v-if="day.date">
              <span class="day-num">{{ day.dayNum }}</span>
              <span v-if="day.signed" class="day-check">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><polyline points="20 6 9 17 4 12"/></svg>
              </span>
              <span v-if="day.catchUp" class="day-catchup-badge">补</span>
            </template>
          </div>
        </div>

        <div class="calendar-stats" v-if="calendar">
          <span>本月签到 <strong>{{ calendar.totalSignedDays }}</strong> 天</span>
          <span>连续 <strong>{{ calendar.currentStreak }}</strong> 天</span>
        </div>
      </div>

      <!-- Action buttons -->
      <div class="action-section">
        <van-button
          v-if="status && !status.todaySigned"
          type="primary"
          round
          block
          size="large"
          :loading="signing"
          @click="doSignIn"
        >
          立即签到 +{{ currentConfig?.basePoints || 0 }}积分
        </van-button>
        <van-button
          v-else-if="status?.todaySigned"
          round
          block
          size="large"
          disabled
        >
          今日已签到
        </van-button>

        <van-button
          v-if="currentConfig?.catchUpEnabled && status && !status.todaySigned"
          plain
          round
          block
          size="small"
          class="catchup-btn"
          @click="showCatchUp = true"
        >
          补签（消耗 {{ currentConfig.catchUpCost }} 积分）
        </van-button>
      </div>

      <!-- Sign-in result popup -->
      <van-action-sheet v-model:show="showResult" title="签到成功">
        <div class="result-content" v-if="signResult">
          <div class="result-points">
            <span class="result-points-value">+{{ signResult.totalPoints }}</span>
            <span class="result-points-label">积分</span>
          </div>
          <div class="result-details">
            <div class="result-row">
              <span>基础积分</span><span>{{ signResult.basePoints }}</span>
            </div>
            <div class="result-row" v-if="signResult.bonusPoints && signResult.bonusPoints > 0">
              <span>连续奖励</span><span class="bonus-text">+{{ signResult.bonusPoints }}</span>
            </div>
            <div class="result-row">
              <span>连续签到</span><span>{{ signResult.streakDay }} 天</span>
            </div>
            <div class="result-row" v-if="signResult.tierReached">
              <span>达成阶梯</span><span class="tier-text">第 {{ signResult.tierReached }} 天</span>
            </div>
          </div>
        </div>
      </van-action-sheet>

      <!-- Catch-up picker -->
      <van-action-sheet v-model:show="showCatchUp" title="补签">
        <div class="catchup-content">
          <p class="catchup-desc">选择要补签的日期（消耗 {{ currentConfig?.catchUpCost }} 积分）</p>
          <div class="catchup-dates">
            <div
              v-for="d in catchUpDates"
              :key="d.date"
              :class="['catchup-date', { disabled: d.signed }]"
              @click="!d.signed && doCatchUp(d.date)"
            >
              <span class="catchup-date-text">{{ d.label }}</span>
              <span v-if="d.signed" class="catchup-date-signed">已签</span>
            </div>
          </div>
        </div>
      </van-action-sheet>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showToast } from '../../utils/toast'
import {
  listActiveConfigs, signIn, catchUp, getCalendar, getStatus,
  type SignInConfig, type SignInResult, type SignInCalendarVO, type SignInStatusVO,
} from '../../api/signin'

const configs = ref<SignInConfig[]>([])
const currentConfig = ref<SignInConfig | null>(null)
const status = ref<SignInStatusVO | null>(null)
const calendar = ref<SignInCalendarVO | null>(null)
const initialLoading = ref(true)
const signing = ref(false)
const showResult = ref(false)
const signResult = ref<SignInResult | null>(null)
const showCatchUp = ref(false)

const weekDays = ['一', '二', '三', '四', '五', '六', '日']

const tierPercent = computed(() => {
  if (!status.value?.nextTierDay) return 0
  return Math.min(100, Math.round((status.value.currentStreak / status.value.nextTierDay) * 100))
})

interface CalendarCell {
  date: string
  dayNum: number
  signed: boolean
  catchUp: boolean
  isToday: boolean
}

const calendarDays = computed<CalendarCell[]>(() => {
  if (!calendar.value) return []
  const days = calendar.value.days || []
  if (days.length === 0) return []

  const firstDate = new Date(days[0].date)
  let firstDow = firstDate.getDay()
  if (firstDow === 0) firstDow = 7
  const padStart = firstDow - 1

  const today = new Date().toISOString().substring(0, 10)
  const cells: CalendarCell[] = []
  for (let i = 0; i < padStart; i++) {
    cells.push({ date: '', dayNum: 0, signed: false, catchUp: false, isToday: false })
  }
  for (const d of days) {
    cells.push({
      date: d.date,
      dayNum: new Date(d.date).getDate(),
      signed: d.signed,
      catchUp: d.catchUp,
      isToday: d.date === today,
    })
  }
  return cells
})

const catchUpDates = computed(() => {
  if (!calendar.value || !currentConfig.value) return []
  const today = new Date()
  const maxDays = currentConfig.value.catchUpMaxDays || 3
  const dates: { date: string; label: string; signed: boolean }[] = []
  const signedSet = new Set((calendar.value.days || []).filter(d => d.signed).map(d => d.date))

  for (let i = 1; i <= maxDays; i++) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const dateStr = d.toISOString().substring(0, 10)
    dates.push({
      date: dateStr,
      label: `${d.getMonth() + 1}月${d.getDate()}日`,
      signed: signedSet.has(dateStr),
    })
  }
  return dates
})

function currentPeriodKey(): string {
  const now = new Date()
  if (currentConfig.value?.periodType === 'WEEKLY') {
    const jan1 = new Date(now.getFullYear(), 0, 1)
    const dayOfYear = Math.floor((now.getTime() - jan1.getTime()) / 86400000) + 1
    const weekNum = Math.ceil((dayOfYear + jan1.getDay()) / 7)
    return `${now.getFullYear()}-W${String(weekNum).padStart(2, '0')}`
  }
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

async function loadStatusAndCalendar() {
  if (!currentConfig.value) return
  try {
    const [statusRes, calRes] = await Promise.all([
      getStatus(currentConfig.value.id),
      getCalendar(currentConfig.value.id, currentPeriodKey()),
    ])
    status.value = statusRes.data.data
    calendar.value = calRes.data.data
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '加载失败')
  }
}

function switchConfig(c: SignInConfig) {
  currentConfig.value = c
  loadStatusAndCalendar()
}

function prevPeriod() {
  showToast.info('当前仅支持查看当期')
}

function nextPeriod() {
  showToast.info('当前仅支持查看当期')
}

async function doSignIn() {
  if (!currentConfig.value) return
  signing.value = true
  try {
    const { data } = await signIn(currentConfig.value.id)
    const result = data.data as SignInResult
    if (result.success) {
      signResult.value = result
      showResult.value = true
      await loadStatusAndCalendar()
    } else {
      showToast.fail(result.message || '签到失败')
    }
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '签到失败')
  } finally {
    signing.value = false
  }
}

async function doCatchUp(date: string) {
  if (!currentConfig.value) return
  try {
    const { data } = await catchUp(currentConfig.value.id, date)
    const result = data.data as SignInResult
    if (result.success) {
      showToast.success('补签成功')
      showCatchUp.value = false
      await loadStatusAndCalendar()
    } else {
      showToast.fail(result.message || '补签失败')
    }
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '补签失败')
  }
}

onMounted(async () => {
  try {
    const { data } = await listActiveConfigs()
    configs.value = data.data || []
    if (configs.value.length > 0) {
      currentConfig.value = configs.value[0]
      await loadStatusAndCalendar()
    }
  } catch (e: any) {
    showToast.fail(e.response?.data?.message || '加载签到活动失败')
  } finally {
    initialLoading.value = false
  }
})
</script>

<style scoped>
.signin-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.loading-wrap {
  margin-top: 60px;
}

.config-tabs {
  display: flex;
  gap: 8px;
  padding: 12px 14px 0;
  overflow-x: auto;
}
.config-tab {
  flex-shrink: 0;
  padding: 6px 16px;
  border-radius: var(--radius-round);
  font-size: 13px;
  color: var(--color-text-secondary);
  background: var(--color-surface);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.config-tab.active {
  background: var(--color-brand);
  color: #fff;
  font-weight: 600;
}

.status-card {
  margin: 12px 14px;
  padding: 20px;
  background: var(--color-brand-gradient);
  border-radius: var(--radius-lg);
  color: #fff;
}
.status-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.balance-label {
  font-size: 12px;
  opacity: 0.8;
}
.balance-value {
  font-size: 32px;
  font-weight: 700;
  margin-top: 2px;
}
.streak-section {
  text-align: center;
}
.streak-value {
  font-size: 28px;
  font-weight: 700;
}
.streak-label {
  font-size: 12px;
  opacity: 0.8;
}

.tier-progress {
  margin-top: 16px;
}
.tier-hint {
  font-size: 12px;
  opacity: 0.9;
  margin-bottom: 8px;
}
.tier-bar-bg {
  height: 6px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 3px;
  overflow: hidden;
}
.tier-bar-fill {
  height: 100%;
  background: #fff;
  border-radius: 3px;
  transition: width 0.4s ease;
}
.tier-bar-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  opacity: 0.7;
  margin-top: 4px;
}

.calendar-section {
  margin: 0 14px 12px;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 14px;
  box-shadow: var(--shadow-sm);
}
.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.calendar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}
.calendar-day-header {
  text-align: center;
  font-size: 11px;
  color: var(--color-text-muted);
  padding: 4px 0 8px;
}
.calendar-day {
  position: relative;
  text-align: center;
  padding: 8px 0;
  border-radius: var(--radius-sm);
  min-height: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.calendar-day.empty {
  visibility: hidden;
}
.calendar-day.signed {
  background: var(--color-brand-subtle);
}
.calendar-day.today {
  background: var(--color-brand);
  color: #fff;
  border-radius: 50%;
}
.calendar-day.today .day-num {
  color: #fff;
  font-weight: 700;
}
.calendar-day.catchup {
  border: 1px dashed var(--color-brand);
}

.day-num {
  font-size: 13px;
  color: var(--color-text-primary);
}
.day-check {
  color: var(--color-brand);
  margin-top: 1px;
}
.calendar-day.today .day-check {
  color: #fff;
}
.day-catchup-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  font-size: 8px;
  background: var(--color-brand);
  color: #fff;
  border-radius: 50%;
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.calendar-stats {
  display: flex;
  justify-content: space-around;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border-light);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.calendar-stats strong {
  color: var(--color-brand);
}

.action-section {
  padding: 0 14px 20px;
}
.catchup-btn {
  margin-top: 10px;
}

.result-content {
  padding: 0 var(--space-4) var(--space-5);
}
.result-points {
  text-align: center;
  padding: 20px 0;
}
.result-points-value {
  font-size: 40px;
  font-weight: 700;
  color: var(--color-brand);
}
.result-points-label {
  font-size: 14px;
  color: var(--color-text-muted);
  margin-left: 4px;
}
.result-details {
  background: var(--color-bg);
  border-radius: var(--radius-md);
  padding: 12px 16px;
}
.result-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border-light);
}
.result-row:last-child {
  border-bottom: none;
}
.bonus-text {
  color: var(--color-brand);
  font-weight: 600;
}
.tier-text {
  color: var(--color-brand);
  font-weight: 600;
}

.catchup-content {
  padding: 0 var(--space-4) var(--space-5);
}
.catchup-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 12px;
}
.catchup-dates {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.catchup-date {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: var(--color-bg);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.catchup-date:active:not(.disabled) {
  transform: scale(0.985);
}
.catchup-date.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.catchup-date-text {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
}
.catchup-date-signed {
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
