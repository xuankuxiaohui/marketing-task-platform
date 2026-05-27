import { ref, computed, type Ref, type ComputedRef, type InjectionKey, provide, inject } from 'vue'
import type { Node, Edge } from '@vue-flow/core'
import type { Step, StepTransition } from '../../../../../api/step'
import type { StepNodeData, TransitionEdgeData } from '../types'

// --- Layout types (migrated from dagLayout.ts) ---

interface LayoutNode {
  stepIndex: number
  code: string
  depth: number
  rowIndex: number
  colIndex: number
}

interface LayoutResult {
  nodes: LayoutNode[]
  rows: LayoutNode[][]
}

// --- Layout algorithm (from dagLayout.ts) ---

function computeLayout(steps: Step[], transitions: StepTransition[]): LayoutResult {
  if (!steps.length) return { nodes: [], rows: [] }

  const stepByCode = new Map<string, Step>()
  for (const s of steps) {
    if (s.code) stepByCode.set(s.code, s)
  }

  const adjIn = new Map<string, string[]>()
  for (const t of transitions) {
    if (!t.stepCode || !t.targetStepCode) continue
    if (!stepByCode.has(t.stepCode) || !stepByCode.has(t.targetStepCode)) continue
    const preds = adjIn.get(t.targetStepCode) || []
    preds.push(t.stepCode)
    adjIn.set(t.targetStepCode, preds)
  }

  const sorted = [...steps].sort((a, b) => a.seq - b.seq)

  for (let i = 1; i < sorted.length; i++) {
    const code = sorted[i].code
    const preds = adjIn.get(code)
    if (!preds || preds.length === 0) {
      adjIn.set(code, [sorted[i - 1].code])
    }
  }

  const depth = new Map<string, number>()
  for (const step of sorted) {
    const preds = adjIn.get(step.code) || []
    if (preds.length === 0) {
      depth.set(step.code, 0)
    } else {
      let maxPredDepth = 0
      for (const p of preds) {
        maxPredDepth = Math.max(maxPredDepth, depth.get(p) ?? 0)
      }
      depth.set(step.code, maxPredDepth + 1)
    }
  }

  const rowMap = new Map<number, LayoutNode[]>()
  for (let i = 0; i < sorted.length; i++) {
    const step = sorted[i]
    const d = depth.get(step.code) ?? 0
    const node: LayoutNode = {
      stepIndex: steps.indexOf(step),
      code: step.code,
      depth: d,
      rowIndex: d,
      colIndex: 0,
    }
    const row = rowMap.get(d) || []
    row.push(node)
    rowMap.set(d, row)
  }

  const rows: LayoutNode[][] = []
  const sortedDepths = [...rowMap.keys()].sort((a, b) => a - b)
  for (const d of sortedDepths) {
    const row = rowMap.get(d)!
    row.sort((a, b) => a.stepIndex - b.stepIndex)
    for (let ci = 0; ci < row.length; ci++) {
      row[ci].colIndex = ci
    }
    rows.push(row)
  }

  return { nodes: rows.flat(), rows }
}

// --- Position constants ---

const NODE_WIDTH = 264
const NODE_HEIGHT = 60
const ROW_GAP = 120
const COL_GAP = 48

function layoutToPositions(layout: LayoutResult): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>()
  for (const row of layout.rows) {
    const rowWidth = row.length * NODE_WIDTH + (row.length - 1) * COL_GAP
    const startX = -rowWidth / 2
    for (const node of row) {
      positions.set(node.code, {
        x: startX + node.colIndex * (NODE_WIDTH + COL_GAP),
        y: node.rowIndex * (NODE_HEIGHT + ROW_GAP),
      })
    }
  }
  return positions
}

// --- Position persistence ---

function loadPositions(taskId: number | null | undefined): Map<string, { x: number; y: number }> {
  if (!taskId) return new Map()
  try {
    const raw = localStorage.getItem(`flow-positions-${taskId}`)
    if (!raw) return new Map()
    const obj = JSON.parse(raw) as Record<string, { x: number; y: number }>
    return new Map(Object.entries(obj))
  } catch {
    return new Map()
  }
}

function savePositions(taskId: number | null | undefined, positions: Map<string, { x: number; y: number }>) {
  if (!taskId) return
  const obj: Record<string, { x: number; y: number }> = {}
  for (const [k, v] of positions) {
    obj[k] = v
  }
  localStorage.setItem(`flow-positions-${taskId}`, JSON.stringify(obj))
}

// --- Composable ---

export interface FlowEditorApi {
  nodes: Ref<Node<StepNodeData>[]>
  edges: Ref<Edge<TransitionEdgeData>[]>
  selectedNodeId: Ref<string | null>
  selectedEdgeId: Ref<string | null>
  steps: Ref<Step[]>
  hasSelection: ComputedRef<boolean>
  selectedStep: ComputedRef<Step | null>
  selectedTransition: ComputedRef<{ transition: StepTransition; isImplicit: boolean } | null>

  initFromData(steps: Step[], transitions: StepTransition[]): void
  getSteps(): Step[]
  getTransitions(): StepTransition[]
  setSteps(data: Step[]): void
  setTransitions(data: StepTransition[]): void

  addNodeFromPalette(type: string, position: { x: number; y: number }): void
  removeNode(code: string): void
  updateNodeData(code: string, fields: Partial<Step>): void

  addConnection(source: string, target: string): void
  removeEdge(id: string): void
  updateEdgeData(id: string, fields: Partial<StepTransition>): void

  selectNode(id: string | null): void
  selectEdge(id: string | null): void
  clearSelection(): void
  saveNodePositions(): void
}

export const FLOW_EDITOR_KEY: InjectionKey<FlowEditorApi> = Symbol('flowEditor')

export function provideFlowEditor(api: FlowEditorApi) {
  provide(FLOW_EDITOR_KEY, api)
}

export function useFlowEditor(): FlowEditorApi {
  const injected = inject(FLOW_EDITOR_KEY)
  if (!injected) throw new Error('useFlowEditor must be used within a FlowEditor provider')
  return injected
}

export function createFlowEditor(taskId?: number | null): FlowEditorApi {
  const nodes: Ref<Node<StepNodeData>[]> = ref([])
  const edges: Ref<Edge<TransitionEdgeData>[]> = ref([])
  const selectedNodeId: Ref<string | null> = ref(null)
  const selectedEdgeId: Ref<string | null> = ref(null)
  const steps: Ref<Step[]> = ref([])
  const transitions: Ref<StepTransition[]> = ref([])

  // Derived
  const selectedStep = computed(() => {
    if (!selectedNodeId.value) return null
    const node = nodes.value.find(n => n.id === selectedNodeId.value)
    return node?.data?.step ?? null
  })

  const selectedTransition = computed(() => {
    if (!selectedEdgeId.value) return null
    const edge = edges.value.find(e => e.id === selectedEdgeId.value)
    if (!edge?.data) return null
    return { transition: edge.data.transition, isImplicit: edge.data.isImplicit }
  })

  const hasSelection = computed(() => selectedNodeId.value !== null || selectedEdgeId.value !== null)

  // --- Conversion: Steps + Transitions -> Nodes + Edges ---

  function buildNodesAndEdges(stepList: Step[], transitionList: StepTransition[]) {
    const positions = loadPositions(taskId)
    const layout = computeLayout(stepList, transitionList)
    const computedPositions = layoutToPositions(layout)

    const branchCountMap = new Map<string, number>()
    for (const t of transitionList) {
      if (t.stepCode) branchCountMap.set(t.stepCode, (branchCountMap.get(t.stepCode) || 0) + 1)
    }

    const newNodes: Node<StepNodeData>[] = stepList.map(step => {
      const pos = positions.get(step.code) || computedPositions.get(step.code) || { x: 0, y: 0 }
      return {
        id: step.code,
        type: 'step' as const,
        position: pos,
        data: {
          step,
          branchCount: branchCountMap.get(step.code) || 0,
        },
      }
    })

    // Explicit edges
    const newEdges: Edge<TransitionEdgeData>[] = transitionList
      .filter(t => t.stepCode && t.targetStepCode)
      .map(t => ({
        id: `e-${t.stepCode}-${t.targetStepCode}-${t.priority}`,
        source: t.stepCode!,
        target: t.targetStepCode,
        type: 'transition' as const,
        data: { transition: t, isImplicit: false },
      }))

    // Implicit edges (sequential fallback)
    const sorted = [...stepList].sort((a, b) => a.seq - b.seq)
    const explicitSrcCodes = new Set(transitionList.filter(t => t.stepCode).map(t => t.stepCode!))
    const depthMap = new Map<string, number>()
    for (const node of layout.nodes) {
      depthMap.set(node.code, node.depth)
    }

    for (let i = 0; i < sorted.length - 1; i++) {
      const src = sorted[i]
      const tgt = sorted[i + 1]
      if (explicitSrcCodes.has(src.code)) continue
      if ((depthMap.get(src.code) ?? 0) === (depthMap.get(tgt.code) ?? 0)) continue
      const alreadyConnected = newEdges.some(e => e.source === src.code && e.target === tgt.code)
      if (alreadyConnected) continue
      newEdges.push({
        id: `i-${src.code}-${tgt.code}`,
        source: src.code,
        target: tgt.code,
        type: 'implicit' as const,
        data: {
          transition: { stepCode: src.code, targetStepCode: tgt.code, priority: 0 },
          isImplicit: true,
        },
      })
    }

    return { newNodes, newEdges }
  }

  // --- Public API ---

  function initFromData(stepList: Step[], transitionList: StepTransition[]) {
    steps.value = [...stepList]
    transitions.value = [...transitionList]
    const { newNodes, newEdges } = buildNodesAndEdges(stepList, transitionList)
    nodes.value = newNodes
    edges.value = newEdges
  }

  function getSteps(): Step[] {
    return nodes.value.filter(n => n.data).map(n => ({ ...n.data!.step }))
  }

  function getTransitions(): StepTransition[] {
    return edges.value
      .filter(e => e.data && !e.data.isImplicit)
      .map(e => ({ ...e.data!.transition }))
  }

  function setSteps(data: Step[]) {
    steps.value = data || []
    const { newNodes, newEdges } = buildNodesAndEdges(steps.value, transitions.value)
    nodes.value = newNodes
    edges.value = newEdges
  }

  function setTransitions(data: StepTransition[]) {
    transitions.value = data || []
    const { newNodes, newEdges } = buildNodesAndEdges(steps.value, transitions.value)
    nodes.value = newNodes
    edges.value = newEdges
  }

  let codeCounter = 0

  function addNodeFromPalette(type: string, position: { x: number; y: number }) {
    codeCounter++
    const code = `step_${type.toLowerCase()}_${codeCounter}`
    const seq = steps.value.length + 1
    const step: Step = {
      seq,
      code,
      name: `步骤${seq}`,
      type,
      description: '',
      flowDesc: '',
      targetValue: type === 'PROGRESS' ? 1 : undefined,
      callbackEventKey: type === 'CALLBACK' ? '' : undefined,
      prizeId: type === 'REWARD' ? undefined : undefined,
      prizeQuantity: type === 'REWARD' ? 1 : undefined,
    }
    steps.value.push(step)
    const newNode: Node<StepNodeData> = {
      id: code,
      type: 'step',
      position,
      data: { step, branchCount: 0 },
    }
    nodes.value = [...nodes.value, newNode]
    selectedNodeId.value = code
    selectedEdgeId.value = null
  }

  function removeNode(code: string) {
    steps.value = steps.value.filter(s => s.code !== code)
    nodes.value = nodes.value.filter(n => n.id !== code)
    edges.value = edges.value.filter(e => e.source !== code && e.target !== code)
    transitions.value = transitions.value.filter(t => t.stepCode !== code && t.targetStepCode !== code)
    if (selectedNodeId.value === code) selectedNodeId.value = null
  }

  function updateNodeData(code: string, fields: Partial<Step>) {
    const node = nodes.value.find(n => n.id === code)
    if (!node?.data) return
    const oldCode = node.data.step.code
    Object.assign(node.data.step, fields)

    // If code changed, update node id and edge references
    if (fields.code && fields.code !== oldCode) {
      const newCode = fields.code
      node.id = newCode
      for (const edge of edges.value) {
        if (edge.source === oldCode) edge.source = newCode
        if (edge.target === oldCode) edge.target = newCode
        if (edge.data?.transition.stepCode === oldCode) edge.data.transition.stepCode = newCode
        if (edge.data?.transition.targetStepCode === oldCode) edge.data.transition.targetStepCode = newCode
      }
      for (const t of transitions.value) {
        if (t.stepCode === oldCode) t.stepCode = newCode
        if (t.targetStepCode === oldCode) t.targetStepCode = newCode
      }
      if (selectedNodeId.value === oldCode) selectedNodeId.value = newCode
    }

    // Update steps array
    const idx = steps.value.findIndex(s => s.code === oldCode || s.code === code)
    if (idx >= 0) steps.value[idx] = node.data.step

    // Trigger reactivity
    nodes.value = [...nodes.value]
    edges.value = [...edges.value]
  }

  function addConnection(source: string, target: string) {
    if (source === target) return
    const exists = edges.value.some(e => e.source === source && e.target === target && !e.data?.isImplicit)
    if (exists) return
    const srcNode = nodes.value.find(n => n.id === source)
    if (srcNode?.data?.step.type === 'REWARD') return

    const existingTransitions = transitions.value.filter(t => t.stepCode === source)
    const maxPrio = existingTransitions.length > 0
      ? Math.max(...existingTransitions.map(t => t.priority)) + 1
      : 0

    const transition: StepTransition = {
      stepCode: source,
      targetStepCode: target,
      priority: maxPrio,
      conditionExpr: undefined,
      description: undefined,
    }
    transitions.value.push(transition)

    // Remove implicit edge between same source/target if exists
    edges.value = edges.value.filter(e => !(e.source === source && e.target === target && e.data?.isImplicit))

    const newEdge: Edge<TransitionEdgeData> = {
      id: `e-${source}-${target}-${maxPrio}`,
      source,
      target,
      type: 'transition',
      data: { transition, isImplicit: false },
    }
    edges.value = [...edges.value, newEdge]
    selectedEdgeId.value = newEdge.id
    selectedNodeId.value = null
  }

  function removeEdge(id: string) {
    const edge = edges.value.find(e => e.id === id)
    if (!edge) return
    if (edge.data && !edge.data.isImplicit) {
      transitions.value = transitions.value.filter(t =>
        !(t.stepCode === edge.data!.transition.stepCode &&
          t.targetStepCode === edge.data!.transition.targetStepCode &&
          t.priority === edge.data!.transition.priority)
      )
    }
    edges.value = edges.value.filter(e => e.id !== id)
    if (selectedEdgeId.value === id) selectedEdgeId.value = null
  }

  function updateEdgeData(id: string, fields: Partial<StepTransition>) {
    const edge = edges.value.find(e => e.id === id)
    if (!edge?.data || edge.data.isImplicit) return
    Object.assign(edge.data.transition, fields)

    // Sync transitions array
    const tIdx = transitions.value.findIndex(t =>
      t.stepCode === edge.data!.transition.stepCode &&
      t.targetStepCode === edge.data!.transition.targetStepCode
    )
    if (tIdx >= 0) Object.assign(transitions.value[tIdx], fields)

    edges.value = [...edges.value]
  }

  function selectNode(id: string | null) {
    selectedNodeId.value = id
    selectedEdgeId.value = null
  }

  function selectEdge(id: string | null) {
    selectedEdgeId.value = id
    selectedNodeId.value = null
  }

  function clearSelection() {
    selectedNodeId.value = null
    selectedEdgeId.value = null
  }

  function saveNodePositions() {
    const positions = new Map<string, { x: number; y: number }>()
    for (const node of nodes.value) {
      positions.set(node.id, node.position)
    }
    savePositions(taskId, positions)
  }

  return {
    nodes,
    edges,
    selectedNodeId,
    selectedEdgeId,
    steps,
    hasSelection,
    selectedStep,
    selectedTransition,
    initFromData,
    getSteps,
    getTransitions,
    setSteps,
    setTransitions,
    addNodeFromPalette,
    removeNode,
    updateNodeData,
    addConnection,
    removeEdge,
    updateEdgeData,
    selectNode,
    selectEdge,
    clearSelection,
    saveNodePositions,
  }
}
