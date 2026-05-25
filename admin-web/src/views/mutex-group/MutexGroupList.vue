<template>
  <el-card>
    <template #header>
      <div class="header">
        <div>
          <span class="page-title">互斥组管理</span>
          <p class="page-sub">管理任务互斥组，同组内任务不能同时进行</p>
        </div>
        <el-button type="primary" @click="openCreate">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建互斥组
        </el-button>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" min-width="140">
        <template #default="{ row }">
          <el-link type="primary" @click="$router.push(`/mutex-groups/${row.id}`)">{{ row.name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="scope" label="作用域" width="130">
        <template #default="{ row }">
          <span :class="['scope-pill', row.scope === 'FULL_LIFECYCLE' ? 'scope-full' : 'scope-cycle']">
            {{ row.scope === 'FULL_LIFECYCLE' ? '全生命周期' : '同周期' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="跨周期" width="90" align="center">
        <template #default="{ row }">
          <span :class="['cross-cycle-pill', row.crossCycle ? 'cross-yes' : 'cross-no']">
            {{ row.crossCycle ? '是' : '否' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="taskCount" label="任务数" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info" round>{{ row.taskCount ?? 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="scope">
          <el-button size="small" type="primary" plain @click="openEdit(scope.row)">编辑</el-button>
          <el-popconfirm title="确定删除此互斥组？仅空组可删除" @confirm="handleDelete(scope.row.id)">
            <template #reference>
              <el-button size="small" type="danger" plain :disabled="(scope.row.taskCount ?? 0) > 0">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- Edit / Create Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑互斥组' : '新建互斥组'" width="480px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：新手任务互斥组" maxlength="64" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="描述互斥组的用途" maxlength="256" />
        </el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="form.scope" style="width:100%">
            <el-option label="同周期互斥（默认）" value="SAME_CYCLE" />
            <el-option label="全生命周期互斥" value="FULL_LIFECYCLE" />
          </el-select>
          <span class="form-hint">
            <template v-if="form.scope === 'SAME_CYCLE'">同周期内组内任务互斥，下一周期可重新参与</template>
            <template v-else>用户完成组内任一任务后，永久不可参与组内其他任务</template>
          </span>
        </el-form-item>
        <el-form-item label="跨周期互斥">
          <el-switch v-model="form.crossCycle" />
          <span class="form-hint">开启后，用户在任何历史周期内完成过组内任务，都无法再次创建实例</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { listMutexGroups, createMutexGroup, updateMutexGroup, deleteMutexGroup, type MutexGroup } from '../../api/mutex-group'

const rows = ref<MutexGroup[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<MutexGroup>({
  name: '',
  description: '',
  scope: 'SAME_CYCLE',
  crossCycle: false,
})

function load() {
  loading.value = true
  listMutexGroups()
    .then(({ data }) => { rows.value = data.data })
    .catch((e: any) => { ElMessage.error(e.response?.data?.message || '加载互斥组列表失败') })
    .finally(() => { loading.value = false })
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  form.name = ''
  form.description = ''
  form.scope = 'SAME_CYCLE'
  form.crossCycle = false
  dialogVisible.value = true
}

function openEdit(row: MutexGroup) {
  isEdit.value = true
  editingId.value = row.id!
  form.name = row.name
  form.description = row.description || ''
  form.scope = row.scope
  form.crossCycle = row.crossCycle ?? false
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateMutexGroup(editingId.value, { ...form })
    } else {
      await createMutexGroup({ ...form })
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteMutexGroup(id)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
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

.scope-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.6;
}
.scope-cycle { background: var(--el-color-primary-light-8); color: var(--color-brand-primary-hover); }
.scope-full { background: var(--color-pink-subtle); color: var(--color-pink-text); }

.cross-cycle-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.6;
}
.cross-yes { background: var(--color-emerald-subtle); color: var(--color-emerald-text); }
.cross-no { background: var(--color-border-light); color: var(--color-text-muted); }

.form-hint {
  display: block;
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 4px;
  line-height: 1.4;
}
</style>
