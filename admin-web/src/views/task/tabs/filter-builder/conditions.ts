export type FilterCategory = 'user-attr' | 'tag' | 'role-org' | 'list' | 'level' | 'grayscale'

export interface FilterFunctionDef {
  name: string
  label: string
  category: FilterCategory
  categoryLabel: string
  paramType: 'string-array' | 'string' | 'number'
  paramHint: string
  paramOptions?: { label: string; value: string }[]
  summaryTemplate: string
}

export interface FilterCondition {
  id: string
  functionName: string
  negated: boolean
  params: string[]
}

export interface FilterVisualState {
  mode: 'visual' | 'raw'
  logicOp: 'AND' | 'OR'
  conditions: FilterCondition[]
}

// --- Label maps ---

export const PROVINCE_LABELS: Record<string, string> = {
  BJ: '北京', SH: '上海', GD: '广东', ZJ: '浙江', JS: '江苏',
  SC: '四川', HB: '湖北', HN: '湖南', FJ: '福建', SD: '山东',
  LN: '辽宁', TJ: '天津', CQ: '重庆', HE: '河北', SX: '山西',
  SN: '陕西', GS: '甘肃', QH: '青海', YN: '云南', GZ: '贵州',
  HI: '海南', JL: '吉林', HLJ: '黑龙江', AH: '安徽', JX: '江西',
  HEN: '河南', GX: '广西', NMG: '内蒙古', XZ: '西藏', NX: '宁夏', XJ: '新疆',
}

const ROLE_OPTIONS = [
  { label: '管理员', value: 'admin' },
  { label: '运营', value: 'operator' },
  { label: '普通用户', value: 'user' },
  { label: 'VIP 用户', value: 'vip' },
]

// --- Function Catalog (14 entries matching backend FilterExpressionEngine) ---

export const FILTER_FUNCTIONS: FilterFunctionDef[] = [
  {
    name: 'inProvince', label: '所在省份', category: 'user-attr', categoryLabel: '用户属性',
    paramType: 'string-array', paramHint: '选择省份',
    paramOptions: Object.entries(PROVINCE_LABELS).map(([k, v]) => ({ label: v, value: k })),
    summaryTemplate: '省份在 [${values}] 中',
  },
  {
    name: 'hasTag', label: '包含标签', category: 'tag', categoryLabel: '标签',
    paramType: 'string', paramHint: '输入标签名',
    summaryTemplate: '包含标签 "${values}"',
  },
  {
    name: 'hasAnyTag', label: '包含任一标签', category: 'tag', categoryLabel: '标签',
    paramType: 'string-array', paramHint: '输入标签，逗号分隔',
    summaryTemplate: '包含标签 [${values}] 中任一',
  },
  {
    name: 'roleEquals', label: '角色等于', category: 'role-org', categoryLabel: '角色/组织',
    paramType: 'string', paramHint: '选择角色',
    paramOptions: ROLE_OPTIONS,
    summaryTemplate: '角色 = "${values}"',
  },
  {
    name: 'roleIn', label: '角色属于', category: 'role-org', categoryLabel: '角色/组织',
    paramType: 'string-array', paramHint: '选择角色',
    paramOptions: ROLE_OPTIONS,
    summaryTemplate: '角色在 [${values}] 中',
  },
  {
    name: 'orgEquals', label: '组织等于', category: 'role-org', categoryLabel: '角色/组织',
    paramType: 'string', paramHint: '输入组织 ID',
    summaryTemplate: '组织 = "${values}"',
  },
  {
    name: 'orgIn', label: '组织属于', category: 'role-org', categoryLabel: '角色/组织',
    paramType: 'string-array', paramHint: '输入组织 ID，逗号分隔',
    summaryTemplate: '组织在 [${values}] 中',
  },
  {
    name: 'inAllowlist', label: '在白名单中', category: 'list', categoryLabel: '名单',
    paramType: 'string', paramHint: '名单 Key',
    summaryTemplate: '在白名单 "${values}" 中',
  },
  {
    name: 'notInDenylist', label: '不在黑名单中', category: 'list', categoryLabel: '名单',
    paramType: 'string', paramHint: '名单 Key',
    summaryTemplate: '不在黑名单 "${values}" 中',
  },
  {
    name: 'levelGte', label: '等级 >=', category: 'level', categoryLabel: '等级',
    paramType: 'number', paramHint: '最小等级',
    summaryTemplate: '等级 >= ${values}',
  },
  {
    name: 'levelEq', label: '等级等于', category: 'level', categoryLabel: '等级',
    paramType: 'number', paramHint: '等级值',
    summaryTemplate: '等级 = ${values}',
  },
  {
    name: 'inGrayPercent', label: '灰度比例', category: 'grayscale', categoryLabel: '灰度/实验',
    paramType: 'number', paramHint: '0-100',
    summaryTemplate: '在 ${values}% 灰度内',
  },
  {
    name: 'inABGroup', label: 'AB 实验组', category: 'grayscale', categoryLabel: '灰度/实验',
    paramType: 'string', paramHint: 'A / B',
    summaryTemplate: '在 AB 实验组 "${values}"',
  },
  {
    name: 'inCrowd', label: '人群包', category: 'grayscale', categoryLabel: '灰度/实验',
    paramType: 'number', paramHint: '人群包 ID',
    summaryTemplate: '在人群包 ${values} 中',
  },
]

export interface FunctionGroup {
  label: string
  fns: FilterFunctionDef[]
}

export function getGroupedFunctions(): FunctionGroup[] {
  const groups = new Map<string, FilterFunctionDef[]>()
  for (const fn of FILTER_FUNCTIONS) {
    if (!groups.has(fn.categoryLabel)) groups.set(fn.categoryLabel, [])
    groups.get(fn.categoryLabel)!.push(fn)
  }
  return Array.from(groups.entries()).map(([label, fns]) => ({ label, fns }))
}

export function getFunctionDef(name: string): FilterFunctionDef | undefined {
  return FILTER_FUNCTIONS.find(f => f.name === name)
}

// --- Generate expression from visual state ---

export function generateExpression(state: FilterVisualState): string {
  if (state.conditions.length === 0) return ''

  const parts = state.conditions
    .filter(c => c.functionName && c.params.length > 0 && c.params.some(p => p !== ''))
    .map(c => {
      const def = getFunctionDef(c.functionName)
      if (!def) return ''
      let args: string
      switch (def.paramType) {
        case 'string-array':
          args = `[${c.params.map(p => `'${p}'`).join(',')}]`
          break
        case 'string':
          args = `'${c.params[0]}'`
          break
        case 'number':
          args = c.params[0]
          break
      }
      const expr = `${c.functionName}(${args})`
      return c.negated ? `!${expr}` : expr
    })
    .filter(Boolean)

  return parts.join(` ${state.logicOp.toLowerCase()} `)
}

// --- Split expression by operator (ignoring inside brackets/parens) ---

function splitByOperator(expr: string, op: string): string[] {
  const parts: string[] = []
  let depth = 0
  let current = ''
  let i = 0
  while (i < expr.length) {
    const ch = expr[i]
    if (ch === '(' || ch === '[') depth++
    else if (ch === ')' || ch === ']') depth--

    if (depth === 0 && expr.substring(i, i + op.length) === op) {
      const trimmed = current.trim()
      if (trimmed) parts.push(trimmed)
      current = ''
      i += op.length
      continue
    }
    current += ch
    i++
  }
  const trimmed = current.trim()
  if (trimmed) parts.push(trimmed)
  return parts
}

// --- Parse expression into visual state ---

export function parseExpression(expression: string): FilterVisualState {
  if (!expression || !expression.trim()) {
    return { mode: 'visual', logicOp: 'AND', conditions: [] }
  }

  let maxDepth = 0, depth = 0
  for (const ch of expression) {
    if (ch === '(') { depth++; maxDepth = Math.max(maxDepth, depth) }
    if (ch === ')') depth--
  }
  if (maxDepth > 1) {
    return { mode: 'raw', logicOp: 'AND', conditions: [] }
  }

  let logicOp: 'AND' | 'OR' = 'AND'
  const orParts = splitByOperator(expression, '||')
  const andParts = splitByOperator(expression, '&&')

  let segments: string[]
  if (orParts.length > 1) {
    logicOp = 'OR'
    segments = orParts
  } else if (andParts.length > 1) {
    segments = andParts
  } else {
    segments = [expression]
  }

  const conditions: FilterCondition[] = []
  for (const seg of segments) {
    const trimmed = seg.trim()
    if (!trimmed) continue

    const m = trimmed.match(/^(!?)(\w+)\((.+)\)$/)
    if (!m) return { mode: 'raw', logicOp: 'AND', conditions: [] }

    const negated = m[1] === '!'
    const fnName = m[2]
    const rawArgs = m[3]

    const def = getFunctionDef(fnName)
    if (!def) return { mode: 'raw', logicOp: 'AND', conditions: [] }

    let params: string[] = []
    switch (def.paramType) {
      case 'string-array': {
        const strMatches = rawArgs.matchAll(/'([^']*)'/g)
        params = Array.from(strMatches, sm => sm[1])
        break
      }
      case 'string': {
        const strMatch = rawArgs.match(/^'(.+)'$/)
        params = strMatch ? [strMatch[1]] : [rawArgs]
        break
      }
      case 'number':
        params = [rawArgs.trim()]
        break
    }

    conditions.push({
      id: crypto.randomUUID(),
      functionName: fnName,
      negated,
      params,
    })
  }

  return { mode: 'visual', logicOp, conditions }
}

// --- Generate human-readable Chinese summary ---

export function generateSummary(conditions: FilterCondition[], logicOp: string): string {
  if (conditions.length === 0) return '未配置条件'

  const joiner = logicOp === 'AND' ? ' 并且 ' : ' 或者 '

  return conditions
    .map(c => {
      const def = getFunctionDef(c.functionName)
      if (!def) return `${c.negated ? '非 ' : ''}${c.functionName}(${c.params.join(',')})`

      let valueDisplay: string
      switch (def.paramType) {
        case 'string-array':
          valueDisplay = c.params
            .map(p => PROVINCE_LABELS[p] || def.paramOptions?.find(o => o.value === p)?.label || p)
            .join(', ')
          break
        case 'string':
          valueDisplay = def.paramOptions?.find(o => o.value === c.params[0])?.label || c.params[0]
          break
        case 'number':
          valueDisplay = c.params[0]
          break
      }

      let text = def.summaryTemplate.replace('${values}', valueDisplay || '?')
      if (c.negated) text = `非(${text})`
      return text
    })
    .join(joiner)
}
