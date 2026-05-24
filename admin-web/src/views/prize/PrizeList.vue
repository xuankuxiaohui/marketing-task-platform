<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">奖品管理</span>
          <p class="page-sub">配置奖品类型、库存和发放规则</p>
        </div>
        <el-button type="primary" @click="$router.push('/prizes/new')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建奖品
        </el-button>
      </div>
    </template>
    <el-table :data="rows">
      <el-table-column prop="id" label="奖品ID" width="80" />
      <el-table-column prop="activityId" label="活动ID" width="80" align="center">
        <template #default="{ row }">
          <span class="activity-id-cell">{{ row.activityId ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140">
        <template #default="{ row }">
          <div class="prize-name">
            <img v-if="row.iconUrl" :src="row.iconUrl" class="prize-icon-sm" />
            <span class="name-cell">{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <span :class="['type-pill', typeClass(row.type)]">{{ typeLabel(row.type) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="totalStock" label="库存" width="90" align="center">
        <template #default="{ row }">
          <span class="stock-cell">{{ row.totalStock ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="每日/月上限" width="110" align="center">
        <template #default="{ row }">
          <span class="limit-cell">{{ row.dailyStock ?? '-' }} / {{ row.monthlyStock ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="自动发放" width="85" align="center">
        <template #default="{ row }">
          <span :class="row.autoGrant ? 'bool-on' : 'bool-off'">{{ row.autoGrant ? '是' : '否' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <span :class="['status-dot', row.enabled ? 'enabled' : 'disabled']">
            {{ row.enabled ? '启用' : '禁用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button size="small" type="primary" plain @click="$router.push(`/prizes/${scope.row.id}`)">编辑</el-button>
          <el-button size="small" :type="scope.row.enabled ? 'warning' : 'success'" plain @click="toggle(scope.row.id)">
            {{ scope.row.enabled ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="load"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listPrizes, togglePrize } from '../../api/prize'

const rows = ref([])
const page = ref(1)
const size = 20
const total = ref(0)

const typeLabel = (t: string) => ({
  POINT: '积分', COUPON: '优惠券', BADGE: '徽章',
  PHYSICAL: '实物', MEMBERSHIP: '会员卡', INTERNAL: '内部'
}[t] || t)

const typeClass = (t: string) => ({
  POINT: 't-point', COUPON: 't-coupon', BADGE: 't-badge',
  PHYSICAL: 't-physical', MEMBERSHIP: 't-membership', INTERNAL: 't-internal'
}[t] || '')

async function load() {
  try {
    const { data } = await listPrizes(page.value, size)
    rows.value = data.data.records || []
    total.value = data.data.total || 0
  } catch (e) {
    console.error('Failed to load prizes:', e)
  }
}

async function toggle(id: number) {
  try {
    await togglePrize(id)
    await load()
  } catch (e) {
    console.error('Failed to toggle prize:', e)
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

.prize-name {
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
.name-cell {
  font-weight: 600;
  color: #2d1b69;
}
.stock-cell, .limit-cell {
  font-variant-numeric: tabular-nums;
  color: #475569;
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

.bool-on { color: #16a34a; font-weight: 600; font-size: 12px; }
.bool-off { color: #94a3b8; font-size: 12px; }

.status-dot {
  font-size: 12px;
  font-weight: 600;
}
.status-dot.enabled { color: #16a34a; }
.status-dot.disabled { color: #94a3b8; }

.activity-id-cell {
  font-variant-numeric: tabular-nums;
  color: #475569;
}

.pager-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
