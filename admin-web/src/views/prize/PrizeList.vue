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
    <el-table :data="rows" v-loading="loading">
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
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
defineOptions({ name: 'PrizeList' })
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listPrizes, togglePrize } from '../../api/prize'

const rows = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
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
  loading.value = true
  try {
    const { data } = await listPrizes(page.value, size.value)
    rows.value = data.data.records || []
    total.value = data.data.total || 0
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载奖品列表失败')
  } finally {
    loading.value = false
  }
}

async function toggle(id: number) {
  try {
    await togglePrize(id)
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
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
  background: var(--color-border-light);
}
.name-cell {
  font-weight: 600;
  color: var(--color-text-primary);
}
.stock-cell, .limit-cell {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.type-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
}
.t-point { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.t-coupon { background: var(--color-amber-subtle); color: var(--color-amber-text); }
.t-badge { background: var(--color-brand-primary-subtle); color: var(--color-brand-primary); }
.t-physical { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.t-membership { background: var(--color-pink-subtle); color: var(--color-pink-text); }
.t-internal { background: var(--color-border-light); color: var(--color-text-muted); }

.bool-on { color: var(--color-published-text); font-weight: 600; font-size: 12px; }
.bool-off { color: var(--color-text-disabled); font-size: 12px; }

.status-dot {
  font-size: 12px;
  font-weight: 600;
}
.status-dot.enabled { color: var(--color-published-text); }
.status-dot.disabled { color: var(--color-text-disabled); }

.activity-id-cell {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.pager-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
