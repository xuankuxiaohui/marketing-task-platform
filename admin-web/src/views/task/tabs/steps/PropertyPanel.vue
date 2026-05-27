<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useFlowEditor } from './composables/useFlowEditor'
import { useValidation } from './composables/useValidation'
import type { Step, StepTransition } from '../../../../api/step'
import { listPrizes } from '../../../../api/prize'

const {
  selectedNodeId,
  selectedEdgeId,
  selectedStep,
  selectedTransition,
  nodes,
  steps,
  updateNodeData,
  updateEdgeData,
  removeEdge,
  removeNode,
} = useFlowEditor()

const {
  codeValidating, codeFeedback, codeValid,
  exprValidating, exprFeedback, exprValid,
  validateCode, validateExpression,
  resetCode, resetExpr,
} = useValidation()

const prizes = ref<any[]>([])

const nodeForm = ref<Partial<Step>>({})
const extraItems = ref<{ key: string; value: string }[]>([])
const editingCode = ref('')

const edgeForm = ref<{
  targetStepCode: string
  priority: number
  conditionExpr: string
  description: string
}>({ targetStepCode: '', priority: 0, conditionExpr: '', description: '' })

const props = defineProps<{ taskId?: number | null }>()

watch(selectedNodeId, (id) => {
  if (!id) return
  const step = selectedStep.value
  if (!step) return
  editingCode.value = step.code
  nodeForm.value = { ...step }
  syncExtraItems(step.extraJson)
  resetCode()
}, { immediate: true })

watch(selectedEdgeId, (id) => {
  if (!id) return
  const tr = selectedTransition.value
  if (!tr) return
  edgeForm.value = {
    targetStepCode: tr.transition.targetStepCode || '',
    priority: tr.transition.priority ?? 0,
    conditionExpr: tr.transition.conditionExpr || '',
    description: tr.transition.description || '',
  }
  resetExpr()
}, { immediate: true })

function syncExtraItems(jsonStr?: string) {
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
      const v = item.value.trim()
      obj[item.key.trim()] = v === 'true' ? true : v === 'false' ? false : /^-?\d+$/.test(v) ? Number(v) : v
    }
  }
  return Object.keys(obj).length > 0 ? JSON.stringify(obj) : ''
}

const otherSteps = computed(() => {
  return steps.value.filter(s => s.code !== editingCode.value)
})

function saveNode() {
  if (!nodeForm.value.code?.trim() || !nodeForm.value.name?.trim()) {
    ElMessage.warning('编码和名称不能为空')
    return
  }
  const extraJson = buildExtraJson()
  const fields: Partial<Step> = {
    code: nodeForm.value.code!.trim(),
    name: nodeForm.value.name!.trim(),
    type: nodeForm.value.type,
    flowDesc: nodeForm.value.flowDesc,
    description: nodeForm.value.description || nodeForm.value.flowDesc,
    extraJson: extraJson || undefined,
    targetValue: nodeForm.value.type === 'PROGRESS' ? nodeForm.value.targetValue : undefined,
    callbackEventKey: nodeForm.value.type === 'CALLBACK' ? nodeForm.value.callbackEventKey : undefined,
    prizeId: nodeForm.value.type === 'REWARD' ? nodeForm.value.prizeId : undefined,
    prizeQuantity: nodeForm.value.type === 'REWARD' ? nodeForm.value.prizeQuantity : undefined,
  }
  updateNodeData(editingCode.value, fields)
  if (fields.code && fields.code !== editingCode.value) {
    editingCode.value = fields.code
  }
  ElMessage.success('步骤已更新')
}

function saveEdge() {
  if (!selectedEdgeId.value) return
  const fields: Partial<StepTransition> = {
    targetStepCode: edgeForm.value.targetStepCode,
    priority: edgeForm.value.priority,
    conditionExpr: edgeForm.value.conditionExpr || undefined,
    description: edgeForm.value.description || undefined,
  }
  updateEdgeData(selectedEdgeId.value, fields)
  ElMessage.success('分支已更新')
}

function handleDeleteNode() {
  if (selectedNodeId.value) removeNode(selectedNodeId.value)
}

function handleDeleteEdge() {
  if (selectedEdgeId.value) removeEdge(selectedEdgeId.value)
}

onMounted(async () => {
  try {
    const { data } = await listPrizes(1, 200)
    prizes.value = data.data.records || []
  } catch {}
})
</script>

<template>
  <transition name="slide">
    <div v-if="selectedNodeId || selectedEdgeId" class="property-panel">
      <template v-if="selectedNodeId && selectedStep">
        <div class="panel-header">
          <span class="panel-title">步骤属性</span>
          <el-button size="small" type="danger" plain @click="handleDeleteNode">删除</el-button>
        </div>
        <el-form label-width="70px" size="small">
          <el-form-item label="编码" required>
            <el-input v-model="nodeForm.code" maxlength="64" @change="validateCode(taskId!, nodeForm.code!, selectedStep?.id)" />
            <span :class="['form-feedback', codeValid === true ? 'valid' : codeValid === false ? 'invalid' : '']">{{ codeFeedback }}</span>
          </el-form-item>
          <el-form-item label="名称" required>
            <el-input v-model="nodeForm.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="nodeForm.type" style="width: 100%">
              <el-option label="点击 CLICK" value="CLICK" />
              <el-option label="回调 CALLBACK" value="CALLBACK" />
              <el-option label="进度 PROGRESS" value="PROGRESS" />
              <el-option label="奖励 REWARD" value="REWARD" />
              <el-option label="被动 PASSIVE" value="PASSIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="nodeForm.flowDesc" type="textarea" :rows="2" maxlength="512" />
          </el-form-item>
          <el-form-item v-if="nodeForm.type === 'PROGRESS'" label="目标值">
            <el-input-number v-model="nodeForm.targetValue" :min="1" />
          </el-form-item>
          <el-form-item v-if="nodeForm.type === 'CALLBACK'" label="事件Key">
            <el-input v-model="nodeForm.callbackEventKey" maxlength="64" />
          </el-form-item>
          <el-form-item v-if="nodeForm.type === 'REWARD'" label="奖品">
            <el-select v-model="nodeForm.prizeId" placeholder="选择奖品" clearable style="width: 100%">
              <el-option v-for="p in prizes" :key="p.id" :label="`[#${p.id}] ${p.name}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="nodeForm.type === 'REWARD'" label="数量">
            <el-input-number v-model="nodeForm.prizeQuantity" :min="1" />
          </el-form-item>
          <el-form-item label="额外配置">
            <div class="extra-json-editor">
              <div v-for="(item, ei) in extraItems" :key="ei" class="extra-row">
                <el-input v-model="item.key" placeholder="key" size="small" style="width: 100px" />
                <el-input v-model="item.value" placeholder="value" size="small" style="width: 120px" />
                <el-button size="small" type="danger" plain @click="extraItems.splice(ei, 1)">x</el-button>
              </div>
              <el-button size="small" @click="extraItems.push({ key: '', value: '' })">+ 添加</el-button>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveNode" size="small">保存步骤</el-button>
          </el-form-item>
        </el-form>
      </template>

      <template v-if="selectedEdgeId && selectedTransition">
        <div class="panel-header">
          <span class="panel-title">{{ selectedTransition.isImplicit ? '隐式连接' : '分支配置' }}</span>
          <el-button v-if="!selectedTransition.isImplicit" size="small" type="danger" plain @click="handleDeleteEdge">删除</el-button>
        </div>
        <template v-if="selectedTransition.isImplicit">
          <div class="implicit-info">
            <p>这是隐式顺序连接（虚线），由系统自动推断。</p>
            <p>如需自定义分支，请从源步骤底部拖线到目标步骤。</p>
          </div>
        </template>
        <template v-else>
          <el-form label-width="70px" size="small">
            <el-form-item label="源步骤">
              <el-input :model-value="selectedTransition.transition.stepCode" disabled />
            </el-form-item>
            <el-form-item label="目标步骤">
              <el-select v-model="edgeForm.targetStepCode" style="width: 100%">
                <el-option v-for="s in otherSteps" :key="s.code" :label="`[${s.seq}] ${s.code}: ${s.name}`" :value="s.code" />
              </el-select>
            </el-form-item>
            <el-form-item label="优先级">
              <el-input-number v-model="edgeForm.priority" :min="0" />
            </el-form-item>
            <el-form-item label="条件">
              <div class="condition-input-wrap">
                <el-input v-model="edgeForm.conditionExpr" placeholder="e.g. hasTag('vip')" />
                <el-button size="small" @click="validateExpression(edgeForm.conditionExpr)" :loading="exprValidating">校验</el-button>
              </div>
              <span :class="['form-feedback', exprValid === true ? 'valid' : exprValid === false ? 'invalid' : '']">{{ exprFeedback }}</span>
              <span v-if="!edgeForm.conditionExpr" class="default-tag">(默认分支)</span>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="edgeForm.description" placeholder="分支说明" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveEdge" size="small">保存分支</el-button>
            </el-form-item>
          </el-form>
        </template>
      </template>
    </div>
  </transition>
</template>

<style scoped>
.property-panel {
  width: 320px;
  flex-shrink: 0;
  padding: 12px 16px;
  border-left: 1px solid var(--color-border-light);
  background: var(--color-surface);
  overflow-y: auto;
  max-height: 100%;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border-light);
}
.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.form-feedback {
  display: block;
  font-size: 11px;
  margin-top: 2px;
}
.form-feedback.valid { color: var(--color-published-text); }
.form-feedback.invalid { color: var(--color-danger); }
.default-tag {
  display: inline-block;
  padding: 0 4px;
  border-radius: 3px;
  background: var(--color-emerald-subtle, #d1fae5);
  color: var(--color-emerald-text, #065f46);
  font-size: 10px;
  font-weight: 600;
  margin-left: 8px;
}
.condition-input-wrap {
  display: flex;
  gap: 4px;
  align-items: center;
  width: 100%;
}
.extra-json-editor { width: 100%; }
.extra-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}
.implicit-info {
  padding: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.slide-enter-active, .slide-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.slide-enter-from, .slide-leave-to {
  transform: translateX(20px);
  opacity: 0;
}
</style>
