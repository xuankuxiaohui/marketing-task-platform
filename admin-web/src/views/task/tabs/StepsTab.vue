<template>
  <div>
    <div class="steps-header">
      <el-button type="primary" size="small" @click="openCreate">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="margin-right:4px;vertical-align:-2px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        添加步骤
      </el-button>
      <span class="steps-hint" v-if="steps.length">拖拽左侧手柄可调整顺序</span>
    </div>

    <div
      v-for="(row, ri) in steps"
      :key="ri"
      class="step-card"
      :class="{ 'step-dragging': dragIndex === ri }"
      draggable="true"
      @dragstart="onDragStart(ri, $event)"
      @dragover.prevent="onDragOver(ri, $event)"
      @dragend="onDragEnd"
      @dragleave="onDragLeave(ri)"
    >
      <div class="step-card-left">
        <span class="drag-handle" title="拖拽排序">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="8" y1="6" x2="16" y2="6"/><line x1="8" y1="12" x2="16" y2="12"/><line x1="8" y1="18" x2="16" y2="18"/></svg>
        </span>
        <span class="step-seq">{{ ri + 1 }}</span>
        <div class="step-info">
          <code class="step-code">{{ row.code || '(未设)' }}</code>
          <span class="step-name">{{ row.name || '未命名步骤' }}</span>
        </div>
      </div>
      <div class="step-card-right">
        <span :class="['type-tag', typeClass(row.type)]">{{ row.type }}</span>
        <span class="step-desc">{{ row.flowDesc || row.description || '' }}</span>
        <el-button size="small" type="primary" plain @click="openEdit(row, ri)">编辑</el-button>
        <el-button size="small" type="danger" plain @click="removeStep(ri)">删除</el-button>
      </div>
    </div>

    <el-empty v-if="!steps.length" description="暂无步骤，点击上方按钮添加" />

    <!-- Edit / Create Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑步骤' : '添加步骤'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码" required>
          <el-input v-model="form.code" placeholder="step_code" maxlength="64" @change="validateCode" />
          <span class="form-feedback" :class="codeValidClass">{{ codeFeedback }}</span>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="步骤名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:200px">
            <el-option label="点击 CLICK" value="CLICK" />
            <el-option label="回调 CALLBACK" value="CALLBACK" />
            <el-option label="进度 PROGRESS" value="PROGRESS" />
            <el-option label="奖励 REWARD" value="REWARD" />
            <el-option label="被动 PASSIVE" value="PASSIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.flowDesc" type="textarea" :rows="2" placeholder="流程描述" maxlength="512" />
        </el-form-item>

        <!-- Type-specific config -->
        <el-form-item v-if="form.type === 'PROGRESS'" label="目标值">
          <el-input-number v-model="form.targetValue" :min="1" />
        </el-form-item>
        <el-form-item v-if="form.type === 'CALLBACK'" label="事件Key">
          <el-input v-model="form.callbackEventKey" placeholder="回调事件唯一标识" maxlength="64" />
        </el-form-item>
        <el-form-item v-if="form.type === 'REWARD'" label="奖品">
          <el-select v-model="form.prizeId" placeholder="选择奖品" clearable style="width:100%">
            <el-option v-for="p in prizes" :key="p.id" :label="`[#${p.id}] ${p.name}${p.activityId ? ' (活动:' + p.activityId + ')' : ''}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type === 'REWARD'" label="数量">
          <el-input-number v-model="form.prizeQuantity" :min="1" />
        </el-form-item>

        <!-- Extra JSON editor -->
        <el-form-item label="额外配置">
          <div class="extra-json-editor">
            <div v-for="(item, ei) in extraItems" :key="ei" class="extra-row">
              <el-input v-model="item.key" placeholder="key" size="small" style="width:140px" />
              <el-input v-model="item.value" placeholder="value" size="small" style="width:200px" />
              <el-button size="small" type="danger" plain @click="extraItems.splice(ei, 1)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </el-button>
            </div>
            <el-button size="small" @click="extraItems.push({ key: '', value: '' })">+ 添加配置项</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { Step } from '../../../api/step'
import { checkStepCode, reorderSteps } from '../../../api/step'
import { listPrizes } from '../../../api/prize'

const steps = ref<Step[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editIndex = ref(-1)
const saving = ref(false)
const prizes = ref<any[]>([])

const form = reactive<Step>({
  seq: 1, code: '', name: '', type: 'CLICK', description: '',
  flowDesc: '', targetValue: 1, callbackEventKey: '',
  prizeId: undefined, prizeQuantity: 1,
})

const extraItems = ref<{ key: string; value: string }[]>([])

const codeFeedback = ref('')
const codeValidClass = ref('')

const props = defineProps<{ taskId?: number | null }>()

// Drag state
const dragIndex = ref(-1)
const dragOverIndex = ref(-1)

function typeClass(t: string) {
  return ({ CLICK: 'type-click', CALLBACK: 'type-callback', PROGRESS: 'type-progress', REWARD: 'type-reward', PASSIVE: 'type-passive' })[t] || 'type-click'
}

function syncExtraItems() {
  const jsonStr = form.extraJson || form.rewardConfigJson
  if (!jsonStr) {
    extraItems.value = []
    return
  }
  try {
    const obj = JSON.parse(jsonStr)
    extraItems.value = Object.entries(obj).map(([k, v]) => ({ key: k, value: String(v) }))
  } catch {
    extraItems.value = []
  }
}

function buildExtraJson(): string {
  const obj: Record<string, any> = {}
  for (const item of extraItems.value) {
    if (item.key.trim()) {
      obj[item.key.trim()] = tryParse(item.value.trim())
    }
  }
  return Object.keys(obj).length > 0 ? JSON.stringify(obj) : ''
}

function tryParse(v: string): any {
  if (v === 'true') return true
  if (v === 'false') return false
  if (/^-?\d+$/.test(v)) return Number(v)
  return v
}

function resetForm() {
  form.code = ''
  form.name = ''
  form.type = 'CLICK'
  form.flowDesc = ''
  form.description = ''
  form.targetValue = 1
  form.callbackEventKey = ''
  form.prizeId = undefined
  form.prizeQuantity = 1
  form.extraJson = ''
  form.rewardConfigJson = ''
  extraItems.value = []
  codeFeedback.value = ''
  codeValidClass.value = ''
}

function openCreate() {
  isEdit.value = false
  editIndex.value = -1
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Step, index: number) {
  isEdit.value = true
  editIndex.value = index
  form.code = row.code
  form.name = row.name
  form.type = row.type
  form.flowDesc = row.flowDesc || row.description || ''
  form.description = row.description || ''
  form.targetValue = row.targetValue || 1
  form.callbackEventKey = row.callbackEventKey || ''
  form.prizeId = row.prizeId
  form.prizeQuantity = row.prizeQuantity || 1
  form.extraJson = row.extraJson || ''
  form.rewardConfigJson = row.rewardConfigJson || ''
  syncExtraItems()
  codeFeedback.value = ''
  codeValidClass.value = ''
  dialogVisible.value = true
}

async function validateCode() {
  if (!form.code.trim() || !props.taskId) {
    codeFeedback.value = ''
    codeValidClass.value = ''
    return
  }
  try {
    const excludeId = isEdit.value && steps.value[editIndex.value]?.id ? steps.value[editIndex.value].id : undefined
    const { data } = await checkStepCode(props.taskId, form.code.trim(), excludeId)
    if (data.data.valid) {
      codeFeedback.value = '编码可用'
      codeValidClass.value = 'valid'
    } else {
      codeFeedback.value = '编码已存在，请更换'
      codeValidClass.value = 'invalid'
    }
  } catch {
    codeFeedback.value = ''
    codeValidClass.value = ''
  }
}

function handleSave() {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.warning('编码和名称不能为空')
    return
  }
  const extraJson = buildExtraJson()
  const stepData: Step = {
    seq: isEdit.value ? steps.value[editIndex.value].seq : steps.value.length + 1,
    code: form.code.trim(),
    name: form.name.trim(),
    type: form.type,
    flowDesc: form.flowDesc,
    description: form.description || form.flowDesc,
    extraJson: extraJson || undefined,
    targetValue: form.type === 'PROGRESS' ? form.targetValue : undefined,
    callbackEventKey: form.type === 'CALLBACK' ? form.callbackEventKey : undefined,
    prizeId: form.type === 'REWARD' ? form.prizeId : undefined,
    prizeQuantity: form.type === 'REWARD' ? form.prizeQuantity : undefined,
  }

  if (isEdit.value) {
    const existing = steps.value[editIndex.value]
    stepData.id = existing.id
    steps.value[editIndex.value] = stepData
  } else {
    steps.value.push(stepData)
  }
  dialogVisible.value = false
}

function removeStep(index: number) {
  steps.value.splice(index, 1)
  steps.value.forEach((s, i) => s.seq = i + 1)
}

// Native HTML5 drag-and-drop
function onDragStart(index: number, e: DragEvent) {
  dragIndex.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(index))
  }
}

function onDragOver(index: number, e: DragEvent) {
  if (dragIndex.value === -1) return
  e.dataTransfer!.dropEffect = 'move'
  dragOverIndex.value = index
}

function onDragLeave(index: number) {
  if (dragOverIndex.value === index) {
    dragOverIndex.value = -1
  }
}

function onDragEnd() {
  if (dragIndex.value === -1 || dragOverIndex.value === -1 || dragIndex.value === dragOverIndex.value) {
    dragIndex.value = -1
    dragOverIndex.value = -1
    return
  }
  const from = dragIndex.value
  const to = dragOverIndex.value
  const item = steps.value.splice(from, 1)[0]
  steps.value.splice(to, 0, item)
  steps.value.forEach((s, i) => { s.seq = i + 1 })

  if (props.taskId && steps.value.every(s => s.id)) {
    const items = steps.value.map(s => ({ id: s.id!, seq: s.seq }))
    reorderSteps(props.taskId, items).catch(() => {})
  }

  dragIndex.value = -1
  dragOverIndex.value = -1
}

function setSteps(data: Step[]) {
  steps.value = data || []
}

onMounted(async () => {
  try {
    const { data } = await listPrizes(1, 200)
    prizes.value = data.data.records || []
  } catch {}
})

defineExpose({ getSteps: () => steps.value, setSteps })
</script>

<style scoped>
.steps-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.steps-hint {
  font-size: 11px;
  color: #a78bfa;
}

.step-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  margin-bottom: 6px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  transition: box-shadow 0.15s, opacity 0.15s;
  user-select: none;
}
.step-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.step-card.step-dragging {
  opacity: 0.4;
}
.step-card:not(.step-dragging) {
  border-top: 2px solid transparent;
}

.step-card-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.step-card-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drag-handle {
  cursor: grab;
  color: #94a3b8;
  padding: 4px;
}
.drag-handle:hover { color: #6d28d9; }
.drag-handle:active { cursor: grabbing; }

.step-seq {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.step-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.step-code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  background: #f5f3ff;
  color: #6d28d9;
  padding: 1px 6px;
  border-radius: 3px;
  width: fit-content;
}
.step-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.step-desc {
  font-size: 11px;
  color: #94a3b8;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  flex-shrink: 0;
}
.type-click { background: #dbeafe; color: #1d4ed8; }
.type-callback { background: #fef3c7; color: #b45309; }
.type-progress { background: #ede9fe; color: #6d28d9; }
.type-reward { background: #d1fae5; color: #047857; }
.type-passive { background: #f1f5f9; color: #64748b; }

.form-feedback {
  display: block;
  font-size: 11px;
  margin-top: 2px;
}
.form-feedback.valid { color: #16a34a; }
.form-feedback.invalid { color: #dc2626; }

.extra-json-editor {
  width: 100%;
}
.extra-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
</style>
