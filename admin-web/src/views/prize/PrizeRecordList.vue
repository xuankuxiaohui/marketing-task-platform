<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">奖品记录</span>
          <p class="page-sub">查询和补发用户奖品记录</p>
        </div>
      </div>
    </template>

    <div class="filter-bar">
      <el-input v-model="filters.userId" placeholder="用户ID" clearable style="width:160px" />
      <el-input v-model.number="filters.prizeId" placeholder="奖品ID" clearable style="width:140px" />
      <el-select v-model="filters.status" placeholder="状态" clearable style="width:130px">
        <el-option label="待领取" value="WON" />
        <el-option label="领取中" value="CLAIMING" />
        <el-option label="已到账" value="GRANTED" />
        <el-option label="失败" value="FAILED" />
        <el-option label="永久失败" value="FAILED_PERMANENTLY" />
        <el-option label="已过期" value="EXPIRED" />
      </el-select>
      <el-date-picker v-model="filters.startDate" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width:150px" />
      <el-date-picker v-model="filters.endDate" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width:150px" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="记录ID" width="90" />
      <el-table-column prop="userId" label="用户ID" width="130" />
      <el-table-column prop="prizeId" label="奖品ID" width="80" align="center" />
      <el-table-column prop="prizeName" label="奖品名称" min-width="120">
        <template #default="{ row }">
          <div class="prize-name-cell">
            <img v-if="row.prizeIcon" :src="row.prizeIcon" class="prize-icon-sm" />
            <span>{{ row.prizeName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="prizeType" label="类型" width="80" align="center">
        <template #default="{ row }">
          <span :class="['type-pill', typeClass(row.prizeType)]">{{ typeLabel(row.prizeType) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <span :class="['status-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误信息" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMessage" class="error-msg">{{ row.errorMessage }}</span>
          <span v-else class="na-text">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="wonAt" label="获得时间" width="160" align="center">
        <template #default="{ row }">
          <span class="time-cell">{{ row.wonAt ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right" align="center">
        <template #default="scope">
          <el-button
            v-if="canReissue(scope.row.status)"
            size="small"
            type="warning"
            plain
            :loading="reissuing === scope.row.id"
            @click="reissue(scope.row.id)"
          >
            补发
          </el-button>
          <span v-else class="na-text">-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="filters.page"
        :page-size="filters.size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="load"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listPrizeRecords, reissuePrizeRecord } from '../../api/prize'
import { ElMessage, ElMessageBox } from 'element-plus'

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const reissuing = ref<number | null>(null)

const filters = reactive({
  page: 1,
  size: 20,
  userId: '',
  prizeId: null as number | null,
  status: '',
  startDate: '',
  endDate: '',
})

const typeLabel = (t: string) => ({
  POINT: '积分', COUPON: '优惠券', BADGE: '徽章',
  PHYSICAL: '实物', MEMBERSHIP: '会员卡', INTERNAL: '内部'
}[t] || t)

const typeClass = (t: string) => ({
  POINT: 't-point', COUPON: 't-coupon', BADGE: 't-badge',
  PHYSICAL: 't-physical', MEMBERSHIP: 't-membership', INTERNAL: 't-internal'
}[t] || '')

const statusLabel = (s: string) => ({
  WON: '待领取', CLAIMING: '领取中', GRANTED: '已到账',
  FAILED: '失败', FAILED_PERMANENTLY: '永久失败', EXPIRED: '已过期'
}[s] || s)

const statusClass = (s: string) => ({
  WON: 's-won', CLAIMING: 's-claiming', GRANTED: 's-granted',
  FAILED: 's-failed', FAILED_PERMANENTLY: 's-permfail', EXPIRED: 's-expired'
}[s] || '')

function canReissue(status: string) {
  return ['FAILED', 'FAILED_PERMANENTLY', 'EXPIRED'].includes(status)
}

async function load() {
  loading.value = true
  try {
    const params: any = { page: filters.page, size: filters.size }
    if (filters.userId) params.userId = filters.userId
    if (filters.prizeId) params.prizeId = filters.prizeId
    if (filters.status) params.status = filters.status
    if (filters.startDate) params.startDate = filters.startDate
    if (filters.endDate) params.endDate = filters.endDate
    const { data } = await listPrizeRecords(params)
    rows.value = data.data.records || []
    total.value = data.data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  load()
}

function reset() {
  filters.userId = ''
  filters.prizeId = null
  filters.status = ''
  filters.startDate = ''
  filters.endDate = ''
  filters.page = 1
  load()
}

async function reissue(id: number) {
  try {
    await ElMessageBox.confirm('确认补发该奖品记录？将重置状态并重新触发发放。', '补发确认', {
      confirmButtonText: '确认补发',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  reissuing.value = id
  try {
    const { data } = await reissuePrizeRecord(id)
    ElMessage.success(data.message || data.data || '补发已触发')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '补发失败')
  } finally {
    reissuing.value = null
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
  color: #2d1b69;
}
.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #a78bfa;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
  align-items: center;
}

.prize-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.prize-icon-sm {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  object-fit: cover;
  background: #f1f5f9;
}

.type-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
}
.t-point { background: #dbeafe; color: #1d4ed8; }
.t-coupon { background: #fef3c7; color: #b45309; }
.t-badge { background: #ede9fe; color: #6d28d9; }
.t-physical { background: #d1fae5; color: #047857; }
.t-membership { background: #fce7f3; color: #be185d; }
.t-internal { background: #f1f5f9; color: #64748b; }

.status-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
}
.s-won { background: #dbeafe; color: #1d4ed8; }
.s-claiming { background: #fef3c7; color: #b45309; }
.s-granted { background: #d1fae5; color: #047857; }
.s-failed { background: #fee2e2; color: #dc2626; }
.s-permfail { background: #fce7f3; color: #be185d; }
.s-expired { background: #f1f5f9; color: #94a3b8; }

.error-msg {
  color: #dc2626;
  font-size: 12px;
}
.na-text {
  color: #94a3b8;
}
.time-cell {
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.pager-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
