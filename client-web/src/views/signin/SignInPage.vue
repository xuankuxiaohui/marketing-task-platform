<template>
  <div class="signin-page">
    <div class="signin-hero">
      <div class="hero-bg">
        <div class="hero-orb orb-1"></div>
        <div class="hero-orb orb-2"></div>
        <div class="hero-orb orb-3"></div>
      </div>
      <div class="hero-content">
        <div class="hero-top">
          <h1 class="hero-title">每日签到</h1>
          <div class="hero-points-btn" @click="$router.push('/points')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <circle cx="12" cy="12" r="10" />
              <path d="M12 6v12M8 10l4-4 4 4M8 14l4 4 4-4" />
            </svg>
            <span v-if="status">{{ status.pointBalance }}</span>
          </div>
        </div>

        <div v-if="configs.length > 1" class="config-tabs-hero">
          <div
            v-for="c in configs"
            :key="c.id"
            :class="['config-pill', { active: currentConfig?.id === c.id }]"
            @click="switchConfig(c)"
          >
            {{ c.name }}
          </div>
        </div>

        <div class="hero-stats" v-if="status">
          <div class="stat-block">
            <div class="stat-big">{{ status.currentStreak }}</div>
            <div class="stat-label">连续签到</div>
          </div>
          <div class="stat-sep"></div>
          <div class="stat-block">
            <div class="stat-big">{{ status.pointBalance }}</div>
            <div class="stat-label">积分余额</div>
          </div>
          <div class="stat-sep"></div>
          <div class="stat-block">
            <div class="stat-big">{{ calendar?.totalSignedDays || 0 }}</div>
            <div class="stat-label">本月签到</div>
          </div>
        </div>

        <div class="tier-section" v-if="status?.nextTierDay">
          <div class="tier-hint">
            再签 <strong>{{ status.nextTierDay - status.currentStreak }}</strong> 天可获
            <strong>{{ status.nextTierBonus }}</strong> 积分
          </div>
          <div class="tier-bar-bg">
            <div class="tier-bar-fill" :style="{ width: tierPercent + '%' }">
              <div class="tier-bar-shimmer"></div>
            </div>
          </div>
          <div class="tier-labels">
            <span>0</span>
            <span>{{ status.nextTierDay }}天</span>
          </div>
        </div>
      </div>
    </div>

    <van-loading v-if="initialLoading" size="24px" vertical class="loading-wrap">加载中...</van-loading>

    <template v-else-if="configs.length === 0">
      <van-empty description="暂无签到活动" />
    </template>

    <template v-else>
      <div class="calendar-card animate-in animate-in-delay-1">
        <div class="calendar-header">
          <div class="cal-nav" @click="prevPeriod">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
          </div>
          <span class="calendar-title">{{ calendar?.periodKey || '' }}</span>
          <div class="cal-nav" @click="nextPeriod">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <div class="calendar-grid" v-if="calendar">
          <div class="cal-day-header" v-for="d in weekDays" :key="d">{{ d }}</div>
          <div
            v-for="(day, i) in calendarDays"
            :key="i"
            :class="['cal-day', { empty: !day.date, signed: day.signed, today: day.isToday, catchup: day.catchUp }]"
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

        <div class="calendar-legend" v-if="calendar">
          <div class="legend-item">
            <span class="legend-dot signed-dot"></span>
            <span>已签到</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot today-dot"></span>
            <span>今天</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot catchup-dot"></span>
            <span>补签</span>
          </div>
        </div>
      </div>

      <div class="action-section animate-in animate-in-delay-2">
        <button
          v-if="status && !status.todaySigned"
          class="sign-btn"
          :class="{ signing }"
          @click="doSignIn"
          :disabled="signing"
        >
          <span class="sign-btn-bg"></span>
          <span class="sign-btn-content">
            <svg v-if="!signing" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
            <span v-if="signing" class="sign-spinner"></span>
            <span>立即签到 +{{ currentConfig?.basePoints || 0 }}积分</span>
          </span>
        </button>
        <div v-else-if="status?.todaySigned" class="signed-done">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="20 6 9 17 4 12"/></svg>
          今日已签到
        </div>

        <button
          v-if="currentConfig?.catchUpEnabled && status && !status.todaySigned"
          class="catchup-btn"
          @click="showCatchUp = true"
        >
          补签（消耗 {{ currentConfig.catchUpCost }} 积分）
        </button>
      </div>

      <van-action-sheet v-model:show="showResult" title="签到成功">
        <div class="result-content" v-if="signResult">
          <div class="result-hero">
            <div class="result-points-ring">
              <span class="result-points-value">+{{ signResult.totalPoints }}</span>
              <span class="result-points-label">积分</span>
            </div>
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
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--color-brand)" stroke-width="2" stroke-linecap="round"><polyline points="9 18 15 12 9 6"/></svg>
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

/* Hero */
.signin-hero {
  position: relative;
  overflow: hidden;
  padding-bottom: 16px;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(160deg, #4f46e5 0%, #7c3aed 50%, #a78bfa 100%);
}

.hero-orb { position: absolute; border-radius: 50%; filter: blur(60px); }
.orb-1 { width: 200px; height: 200px; background: rgba(236, 72, 153, 0.3); top: -60px; right: -40px; animation: float 10s ease-in-out infinite; }
.orb-2 { width: 160px; height: 160px; background: rgba(6, 182, 212, 0.25); bottom: -40px; left: -30px; animation: float 12s ease-in-out infinite 3s; }
.orb-3 { width: 120px; height: 120px; background: rgba(251, 191, 36, 0.2); top: 30%; left: 40%; animation: float 8s ease-in-out infinite 1s; }

.hero-content {
  position: relative;
  z-index: 1;
  padding: 48px 20px 0;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hero-title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  color: #fff;
}

.hero-points-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border-radius: var(--radius-round);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.15);
  transition: all 0.2s ease;
}

.hero-points-btn:active { transform: scale(0.95); }

.config-tabs-hero {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  overflow-x: auto;
}

.config-pill {
  flex-shrink: 0;
  padding: 6px 16px;
  border-radius: var(--radius-round);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: all 0.2s ease;
}

.config-pill.active {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-weight: 600;
}

.hero-stats {
  display: flex;
  align-items: center;
  justify-content: space-around;
  margin-top: 20px;
  padding: 18px 12px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(12px);
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.stat-block { text-align: center; flex: 1; }
.stat-big { font-size: 26px; font-weight: 800; color: #fff; line-height: 1.2; }
.stat-label { font-size: 11px; color: rgba(255, 255, 255, 0.55); margin-top: 4px; }
.stat-sep { width: 1px; height: 32px; background: rgba(255, 255, 255, 0.15); }

.tier-section { margin-top: 16px; }
.tier-hint { font-size: 12px; color: rgba(255, 255, 255, 0.7); margin-bottom: 8px; }
.tier-hint strong { color: #fff; }
.tier-bar-bg { height: 8px; background: rgba(255, 255, 255, 0.15); border-radius: 4px; overflow: hidden; }
.tier-bar-fill { height: 100%; background: #fff; border-radius: 4px; transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1); position: relative; }
.tier-bar-shimmer { position: absolute; inset: 0; background: linear-gradient(90deg, transparent, rgba(99,102,241,0.2), transparent); background-size: 200% 100%; animation: shimmer 2s infinite; }
.tier-labels { display: flex; justify-content: space-between; font-size: 10px; color: rgba(255,255,255,0.5); margin-top: 4px; }

/* Calendar */
.calendar-card {
  margin: -4px 16px 12px;
  position: relative;
  z-index: 2;
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  padding: 16px;
  box-shadow: var(--shadow-md);
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.cal-nav {
  width: 32px; height: 32px; border-radius: 10px; background: var(--color-bg);
  display: flex; align-items: center; justify-content: center; cursor: pointer;
  color: var(--color-text-secondary); transition: all 0.2s ease;
}

.cal-nav:active { transform: scale(0.9); background: var(--color-brand-subtle); color: var(--color-brand); }
.calendar-title { font-size: 16px; font-weight: 700; color: var(--color-text-primary); }

.calendar-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
.cal-day-header { text-align: center; font-size: 11px; color: var(--color-text-muted); padding: 4px 0 8px; font-weight: 500; }

.cal-day {
  position: relative; text-align: center; padding: 8px 0; border-radius: 10px;
  min-height: 40px; display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.cal-day.empty { visibility: hidden; }
.cal-day.signed { background: var(--color-brand-subtle); }
.cal-day.today { background: var(--color-brand); color: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(99,102,241,0.3); }
.cal-day.today .day-num { color: #fff; font-weight: 700; }
.cal-day.catchup { border: 1.5px dashed var(--color-brand); }

.day-num { font-size: 13px; font-weight: 500; color: var(--color-text-primary); }
.day-check { color: var(--color-brand); margin-top: 1px; }
.cal-day.today .day-check { color: #fff; }
.day-catchup-badge { position: absolute; top: 2px; right: 2px; font-size: 8px; background: var(--color-brand); color: #fff; border-radius: 50%; width: 14px; height: 14px; display: flex; align-items: center; justify-content: center; font-weight: 600; }

.calendar-legend {
  display: flex; justify-content: center; gap: 16px; margin-top: 12px;
  padding-top: 12px; border-top: 1px solid var(--color-border-light);
}
.legend-item { display: flex; align-items: center; gap: 5px; font-size: 11px; color: var(--color-text-muted); }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; }
.signed-dot { background: var(--color-brand-subtle); border: 1px solid var(--color-brand); }
.today-dot { background: var(--color-brand); }
.catchup-dot { background: transparent; border: 1.5px dashed var(--color-brand); }

/* Action */
.action-section { padding: 0 16px 24px; }

.sign-btn {
  width: 100%; height: 56px; border: none; border-radius: 18px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6, #a78bfa);
  background-size: 200% 200%; color: #fff; font-size: 17px; font-weight: 700;
  font-family: var(--font-sans); cursor: pointer; position: relative;
  overflow: hidden; transition: all 0.3s ease;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.35);
}
.sign-btn:active:not(.signing) { transform: scale(0.98); }
.sign-btn.signing { opacity: 0.8; cursor: not-allowed; }

.sign-btn-bg {
  position: absolute; inset: 0;
  background: linear-gradient(135deg, #4f46e5, #7c3aed, #a78bfa);
  background-size: 200% 200%; animation: gradient-shift 3s ease infinite;
}

.sign-btn-content {
  position: relative; z-index: 1;
  display: flex; align-items: center; justify-content: center; gap: 8px;
}

.sign-spinner {
  display: inline-block; width: 20px; height: 20px;
  border: 2.5px solid rgba(255,255,255,0.3); border-top-color: #fff;
  border-radius: 50%; animation: spin 0.6s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.signed-done {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  width: 100%; height: 56px; background: var(--color-status-success-bg);
  color: var(--color-status-success-text); border-radius: 18px;
  font-size: 16px; font-weight: 600;
}

.catchup-btn {
  width: 100%; height: 44px; border: 1.5px solid var(--color-brand-subtle);
  border-radius: 14px; background: transparent; color: var(--color-brand);
  font-size: 14px; font-weight: 600; font-family: var(--font-sans);
  cursor: pointer; margin-top: 10px; transition: all 0.2s ease;
}
.catchup-btn:active { transform: scale(0.98); background: var(--color-brand-subtle); }

/* Result */
.result-content { padding: 0 20px 24px; }
.result-hero { text-align: center; padding: 24px 0; }
.result-points-ring {
  display: inline-flex; flex-direction: column; align-items: center; justify-content: center;
  width: 120px; height: 120px; border-radius: 50%;
  background: var(--color-brand-subtle); border: 3px solid var(--color-brand);
}
.result-points-value { font-size: 32px; font-weight: 800; color: var(--color-brand); }
.result-points-label { font-size: 13px; color: var(--color-text-muted); margin-top: 2px; }

.result-details { background: var(--color-bg); border-radius: var(--radius-md); padding: 12px 16px; }
.result-row { display: flex; justify-content: space-between; padding: 10px 0; font-size: 13px; color: var(--color-text-secondary); border-bottom: 1px solid var(--color-border-light); }
.result-row:last-child { border-bottom: none; }
.bonus-text { color: var(--color-brand); font-weight: 700; }
.tier-text { color: var(--color-brand); font-weight: 700; }

/* Catch-up */
.catchup-content { padding: 0 20px 24px; }
.catchup-desc { font-size: 13px; color: var(--color-text-muted); margin-bottom: 12px; }
.catchup-dates { display: flex; flex-direction: column; gap: 8px; }
.catchup-date {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px; background: var(--color-bg); border-radius: var(--radius-md);
  cursor: pointer; transition: all 0.2s ease;
}
.catchup-date:active:not(.disabled) { transform: scale(0.98); background: var(--color-brand-subtle); }
.catchup-date.disabled { opacity: 0.5; cursor: not-allowed; }
.catchup-date-text { font-size: 14px; color: var(--color-text-primary); font-weight: 500; }
.catchup-date-signed { font-size: 12px; color: var(--color-text-muted); }

.loading-wrap { margin-top: 60px; }
</style>
